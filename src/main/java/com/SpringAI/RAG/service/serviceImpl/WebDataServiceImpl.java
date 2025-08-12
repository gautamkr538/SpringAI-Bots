/*
package com.SpringAI.RAG.service.serviceImpl;

import com.SpringAI.RAG.exception.CrawlException;
import com.SpringAI.RAG.exception.ContentExtractionException;
import com.SpringAI.RAG.exception.DatabaseException;
import com.SpringAI.RAG.service.ChatService;
import com.SpringAI.RAG.service.WebDataService;
import com.SpringAI.RAG.utils.WebDataUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class WebDataServiceImpl implements WebDataService {

    private static final Logger log = LoggerFactory.getLogger(WebDataServiceImpl.class);
    private static final int MAX_CRAWL_DEPTH = 3;
    private static final int RETRY_LIMIT = 3;
    private static final int THREAD_POOL_SIZE = 10;

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final ChatClient chatClient;
    private final ExecutorService executorService;
    private final Semaphore semaphore;
    private final ChatService chatService;

    public WebDataServiceImpl(VectorStore vectorStore, JdbcTemplate jdbcTemplate, ChatClient.Builder chatClientBuilder, ChatService chatService) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.chatClient = chatClientBuilder.build();
        this.chatService = chatService;
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.semaphore = new Semaphore(THREAD_POOL_SIZE);
    }

    @Override
    public List<String> crawlAndExtractContent(String url) {
        Set<String> visitedLinks = ConcurrentHashMap.newKeySet();
        Set<String> extractedContent = ConcurrentHashMap.newKeySet();
        Queue<Future<?>> tasks = new LinkedList<>();
        tasks.add(submitCrawlTask(url, visitedLinks, extractedContent, 0));
        awaitCompletion(tasks);
        shutdownExecutorService(executorService);
        return new ArrayList<>(extractedContent);
    }

    private Future<?> submitCrawlTask(String url, Set<String> visitedLinks, Set<String> extractedContent, int depth) {
        return executorService.submit(() -> {
            try {
                semaphore.acquire();
                crawlAndExtractHelper(url, visitedLinks, extractedContent, depth);
            } catch (InterruptedException e) {
                log.error("Crawl task interrupted for URL: {}, Message: {}", url, e.getMessage());
                throw new CrawlException("Crawl task interrupted", e);
            } finally {
                semaphore.release();
            }
        });
    }

    private void crawlAndExtractHelper(String url, Set<String> visitedLinks, Set<String> extractedContent, int depth) {
        if (visitedLinks.contains(url) || depth >= MAX_CRAWL_DEPTH || !WebDataUtils.isValidUrl(url)) {
            return;
        }
        visitedLinks.add(url);
        if (WebDataUtils.shouldSkipUrl(url)) {
            log.info("Skipping URL: {}", url);
            return;
        }
        try {
            String pageContent;
            if (url.contains("dynamic") || url.contains("javascript")) {
                log.info("JavaScript-heavy page detected: {}", url);
                pageContent = WebDataUtils.processJavaScriptPage(url);
            } else {
                pageContent = fetchPageContent(url);
            }
            if (pageContent == null || pageContent.isEmpty()) {
                return;
            }
            org.jsoup.nodes.Document doc = Jsoup.parse(pageContent, url);
            WebDataUtils.aggregateContent(doc, extractedContent);
            WebDataUtils.extractSpecialContent(pageContent, extractedContent, doc);

            Elements links = doc.select("a[href]");
            for (Element link : links) {
                String nextLink = link.absUrl("href");
                if (WebDataUtils.isValidUrl(nextLink) && !visitedLinks.contains(nextLink)) {
                    submitCrawlTask(nextLink, visitedLinks, extractedContent, depth + 1);
                }
            }
        } catch (Exception e) {
            log.error("Error while crawling URL: {}. Message: {}", url, e.getMessage());
            throw new CrawlException("Error while crawling URL: " + url, e);
        }
    }

    public String fetchPageContent(String url) {
        try {
            return WebDataUtils.fetchUrlWithRetries(url, RETRY_LIMIT)
                    .map(org.jsoup.nodes.Document::outerHtml)
                    .orElseThrow(() -> new CrawlException("Failed to fetch page content for URL: " + url, null));
        } catch (Exception e) {
            log.error("Error fetching page content: {}", e.getMessage());
            throw new CrawlException("Error fetching page content for URL: " + url, e);
        }
    }

    private void awaitCompletion(Queue<Future<?>> tasks) {
        while (!tasks.isEmpty()) {
            try {
                Future<?> task = tasks.poll();
                if (task != null) {
                    task.get();
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("Error while waiting for task completion: {}", e.getMessage());
                throw new CrawlException("Error while waiting for task completion", e);
            }
        }
    }

    private void shutdownExecutorService(ExecutorService executorService) {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            throw new CrawlException("Error shutting down executor service", e);
        }
    }

    @Override
    @Transactional
    public void storeContent(List<String> contentList) {
        if (contentList == null || contentList.isEmpty()) {
            log.warn("Received an empty content list for storage.");
            return;
        }
        try {
            jdbcTemplate.update("DELETE FROM vector_store");
            int batchSize = 100;
            List<Document> batchDocuments = new ArrayList<>();
            for (int i = 0; i < contentList.size(); i++) {
                batchDocuments.add(new Document(contentList.get(i)));
                if (batchDocuments.size() >= batchSize || i == contentList.size() - 1) {
                    log.info("Storing {} content items into the vector_store.", batchDocuments.size());
                    vectorStore.add(batchDocuments);
                    batchDocuments.clear();
                }
            }
            log.info("Successfully stored content into the vector_store.");
        } catch (Exception e) {
            log.error("Error occurred while storing content into the vector_store: {}", e.getMessage());
            throw new DatabaseException("Failed to store content into the vector_store.", e);
        }
    }

    @Override
    public String queryContent(String query) {
        try {
            List<Document> similarDocuments = vectorStore.similaritySearch(query);
            if (similarDocuments.isEmpty()) {
                return "No similar content found in the vector store.";
            }
            String documents = similarDocuments.stream()
                    .map(Document::getFormattedContent)
                    .collect(Collectors.joining("\n"));
            String prompt = """
                    Based on the DOCUMENTS below, respond to the QUERY.
                    If the answer is not available, state: "The data is not available in the provided document."

                    DOCUMENTS:
                    {documents}

                    QUERY:
                    {query}
                    """.replace("{documents}", documents).replace("{query}", query);
            return chatClient.prompt(new Prompt(List.of(new SystemMessage(prompt), new UserMessage(query)))).call().content();
        } catch (Exception e) {
            log.error("Error occurred while querying the content: {}", e.getMessage());
            throw new ContentExtractionException("Error while querying content", e);
        }
    }
}
*/
