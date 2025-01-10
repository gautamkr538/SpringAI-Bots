package com.SpringAI.RAG.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WebDataService {

    private static final Logger log = LoggerFactory.getLogger(WebDataService.class);

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final ChatClient chatClient;

    public WebDataService(VectorStore vectorStore, JdbcTemplate jdbcTemplate, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.chatClient = chatClientBuilder.build();
    }

    public List<String> crawlAndExtractContent(String url) throws IOException {
        Set<String> visitedLinks = new HashSet<>();
        Set<String> extractedContent = new HashSet<>();
        crawlAndExtractHelper(url, visitedLinks, extractedContent);
        return new ArrayList<>(extractedContent);
    }

    private void crawlAndExtractHelper(String url, Set<String> visitedLinks, Set<String> extractedContent) throws IOException {
        if (visitedLinks.contains(url)) {
            return;
        }
        visitedLinks.add(url);
        try {
            org.jsoup.nodes.Document doc = Jsoup.connect(url).execute().parse();
            String textContent = doc.body().text();
            log.info("Extracted Content: {}", textContent);
            extractedContent.add(textContent);

            Elements links = doc.select("a[href]");
            for (Element link : links) {
                String nextLink = link.absUrl("href");
                // Ensure valid and non-visited link
                if (nextLink.startsWith("http://") || nextLink.startsWith("https://")) {
                    crawlAndExtractHelper(nextLink, visitedLinks, extractedContent);
                }
            }
        } catch (Exception e) {
            log.error("Error while crawling URL: {}", url, e);
        }
    }

    public void storeContent(List<String> contentList) {
        if (contentList == null || contentList.isEmpty()) {
            log.warn("Received an empty content list for storage.");
            return;
        }
        try {
            jdbcTemplate.update("DELETE FROM vector_store");
            // Convert content to documents and store them
            List<Document> newDocuments = contentList.stream()
                    .map(Document::new)
                    .collect(Collectors.toList());
            log.info("Storing {} content items into the vector database.", newDocuments.size());
            vectorStore.add(newDocuments);
            log.info("Successfully stored content into the vector database.");
        } catch (Exception e) {
            log.error("Error occurred while storing content into the vector database: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to store content into the vector database.", e);
        }
    }

    public String queryContent(String query) {
        try {
            log.info("Searching for similar content in the vector database for query: {}", query);
            Document queryDocument = new Document(query);
            List<Document> similarDocuments = vectorStore.similaritySearch(String.valueOf(queryDocument));
            if (similarDocuments.isEmpty()) return "The data is not available in the provided document.";
            String documents = similarDocuments.stream().map(Document::getContent).collect(Collectors.joining("\n"));
            String template = """
            Based on the DOCUMENTS below, respond to the QUERY.
            If the answer is not available, state: "The data is not available in the provided document."

            DOCUMENTS:
            {documents}

            QUERY:
            {query}
            """;
            String formattedPrompt = template.replace("{documents}", documents).replace("{query}", query);
            SystemMessage systemMessage = new SystemMessage(formattedPrompt);
            UserMessage userMessage = new UserMessage(query);
            Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
            return chatClient.prompt(prompt).call().content();
        } catch (Exception e) {
            log.error("Error occurred while processing the query: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process the query.", e);
        }
    }
}