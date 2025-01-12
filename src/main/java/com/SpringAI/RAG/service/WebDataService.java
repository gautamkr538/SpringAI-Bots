package com.SpringAI.RAG.service;

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

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class WebDataService {

    private static final Logger log = LoggerFactory.getLogger(WebDataService.class);
    private static final int MAX_CRAWL_DEPTH = 3;
    private static final int RETRY_LIMIT = 3;
    private static final int THREAD_POOL_SIZE = 10;

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final ChatClient chatClient;
    private final ExecutorService executorService;

    public WebDataService(VectorStore vectorStore, JdbcTemplate jdbcTemplate, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.chatClient = chatClientBuilder.build();
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    }

    public List<String> crawlAndExtractContent(String url) {
        Set<String> visitedLinks = ConcurrentHashMap.newKeySet();
        Set<String> extractedContent = ConcurrentHashMap.newKeySet();
        Queue<Future<?>> tasks = new LinkedList<>();
        tasks.add(submitCrawlTask(url, visitedLinks, extractedContent, 0));
        awaitCompletion(tasks);
        executorService.shutdown();
        return new ArrayList<>(extractedContent);
    }

    private Future<?> submitCrawlTask(String url, Set<String> visitedLinks, Set<String> extractedContent, int depth) {
        return executorService.submit(() -> crawlAndExtractHelper(url, visitedLinks, extractedContent, depth));
    }

    private void crawlAndExtractHelper(String url, Set<String> visitedLinks, Set<String> extractedContent, int depth) {
        if (visitedLinks.contains(url) || depth >= MAX_CRAWL_DEPTH || !isValidUrl(url)) {
            return;
        }
        visitedLinks.add(url);
        if (shouldSkipUrl(url)) {
            log.info("Skipping URL: {}", url);
            return;
        }
        try {
            String pageContent = fetchPageContent(url);
            if (pageContent == null) {
                return;
            }
            org.jsoup.nodes.Document doc = Jsoup.parse(pageContent, url);
            aggregateContent(doc, extractedContent);
            extractSpecialContent(pageContent, extractedContent, doc);

            Elements links = doc.select("a[href]");
            for (Element link : links) {
                String nextLink = link.absUrl("href");
                if (isValidUrl(nextLink) && !visitedLinks.contains(nextLink)) {
                    submitCrawlTask(nextLink, visitedLinks, extractedContent, depth + 1);
                }
            }
        } catch (Exception e) {
            log.error("Error while crawling URL: {}. Message: {}", url, e.getMessage());
        }
    }

    private boolean shouldSkipUrl(String url) {
        return url.matches(".*\\.(jpg|jpeg|png|gif|bmp|mp4|webm|mp3|wav|ogg|flac|avi|mov|wmv|mkv|pdf|docx|pptx|xlsx)$");
    }

    private void extractSpecialContent(String text, Set<String> extractedContent, org.jsoup.nodes.Document doc) {
        String githubPattern = "https?://(www\\.)?github\\.com/[a-zA-Z0-9_-]+(/[a-zA-Z0-9._-]+)*(\\?[a-zA-Z0-9=&%]+)?";
        String linkedinPattern = "https?://(www\\.)?linkedin\\.com/(in/[a-zA-Z0-9_-]+(/[a-zA-Z0-9._-]+)*)|(company/[a-zA-Z0-9_-]+(/[a-zA-Z0-9._-]+)*)";
        String socialMediaPattern = "https?://(www\\.)?(facebook|twitter|instagram|pinterest)\\.com/[a-zA-Z0-9_-]+";
        String jobPostingPattern = "https?://(www\\.)?linkedin\\.com/jobs/view/\\d+";
        String academicPattern = "https?://(www\\.)?researchgate\\.net/publication/\\d+";
        // Extract phone numbers and emails from the text
        extractPhoneNumbers(text, extractedContent);
        extractEmails(text, extractedContent);
        // Extract specific types of links
        Elements links = doc.select("a[href]");
        for (Element link : links) {
            String href = link.attr("href");
            if (href.matches(githubPattern)) {
                extractedContent.add("GitHub link: " + href);
            } else if (href.matches(linkedinPattern)) {
                extractedContent.add("LinkedIn link: " + href);
            } else if (href.matches(socialMediaPattern)) {
                extractedContent.add("Social Media link: " + href);
            } else if (href.matches(jobPostingPattern)) {
                extractedContent.add("Job Posting: " + href);
            } else if (href.matches(academicPattern)) {
                extractedContent.add("Academic Publication: " + href);
            }
        }
    }

    private void extractPhoneNumbers(String text, Set<String> extractedContent) {
        Pattern phonePattern = Pattern.compile("(\\+\\d{1,3}[-.\\s]?)?(\\(?\\d{1,4}\\)?[-.\\s]?)?(\\d{1,4}[-.\\s]?){1,3}\\d{1,4}(\\s?x\\d+)?");
        Matcher phoneMatcher = phonePattern.matcher(text);
        while (phoneMatcher.find()) {
            extractedContent.add("Phone number: " + phoneMatcher.group());
        }
    }

    private void extractEmails(String text, Set<String> extractedContent) {
        Pattern emailPattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(\\.[a-zA-Z]{2,})?");
        Matcher emailMatcher = emailPattern.matcher(text);
        while (emailMatcher.find()) {
            extractedContent.add("Email: " + emailMatcher.group());
        }
    }

    private void aggregateContent(org.jsoup.nodes.Document doc, Set<String> extractedContent) {
        Elements headers = doc.select("h1, h2, h3");
        for (Element header : headers) {
            String section = header.text();
            String content = extractSectionContent(header);
            if (!content.isEmpty()) {
                log.info("Extracted Section: {} - {}", section, content);
                extractedContent.add(section + ":\n" + content);
            }
        }
    }

    private String extractSectionContent(Element header) {
        StringBuilder sectionContent = new StringBuilder();
        Element sibling = header.nextElementSibling();

        while (sibling != null && !sibling.tagName().matches("h1|h2|h3")) {
            sectionContent.append(sibling.text()).append("\n");
            sibling = sibling.nextElementSibling();
        }
        return sectionContent.toString().trim();
    }

    private boolean isValidUrl(String url) {
        return url != null && !url.isEmpty() && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private String fetchPageContent(String url) {
        int attempts = 0;
        while (attempts < RETRY_LIMIT) {
            try {
                org.jsoup.nodes.Document doc = Jsoup.connect(url)
                        .timeout(10000)
                        .userAgent("Mozilla/5.0")
                        .get();
                return doc.outerHtml();
            } catch (Exception e) {
                attempts++;
                log.warn("Attempt {} to fetch URL failed: {}. Error: {}", attempts, url, e.getMessage());
            }
        }
        return null;
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
            }
        }
    }

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
            log.error("Error occurred while storing content into the vector_store: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to store content into the vector_store.", e);
        }
    }

    public String queryContent(String query) {
        try {
            List<Document> similarDocuments = vectorStore.similaritySearch(query);
            if (similarDocuments.isEmpty()) {
                return "No similar content found in the vector store.";
            }
            String documents = similarDocuments.stream()
                    .map(Document::getContent)
                    .collect(Collectors.joining("\n"));
            String prompt = """
                    Based on the DOCUMENTS below, respond to the QUERY.
                    If the answer is not available, state: "The data is not available in the provided document."

                    DOCUMENTS:
                    {documents}

                    QUERY:
                    {query}
                    """.replace("{documents}", documents).replace("{query}", query);
            Prompt chatPrompt = new Prompt(List.of(new SystemMessage(prompt), new UserMessage(query)));
            return chatClient.prompt(chatPrompt).call().content();
        } catch (Exception e) {
            log.error("Error occurred while querying the content: {}", e.getMessage());
            throw new RuntimeException("Failed to query the content.");
        }
    }
}