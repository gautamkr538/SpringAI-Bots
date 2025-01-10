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
        List<String> extractedContent = new ArrayList<>();
        visitedLinks.add(url);
        try {
            // Fetch and parse HTML content
            org.jsoup.nodes.Document doc = Jsoup.connect(url).execute().parse();
            String textContent = doc.body().text();
            log.info("Extracted Content: {}", textContent);
            extractedContent.add(textContent);
            Elements links = doc.select("a[href]");
            for (Element link : links) {
                String nextLink = link.absUrl("href");
                if (nextLink.startsWith("http://") || nextLink.startsWith("https://")) {
                    if (!visitedLinks.contains(nextLink)) {
                        // Recursive call for valid link
                        extractedContent.addAll(crawlAndExtractContent(nextLink));
                    }
                } else {
                    log.warn("Skipping non-HTTP URL: {}", nextLink);
                }
            }
        } catch (org.jsoup.HttpStatusException e) {
            log.warn("HTTP error while crawling URL: {}. Status: {}. Skipping...", url, e.getStatusCode());
            throw new IOException("HTTP error while crawling URL", e);
        } catch (org.jsoup.UnsupportedMimeTypeException e) {
            log.warn("Unsupported MIME type for URL: {}. Skipping...", url);
            throw new IOException("Unsupported MIME type", e);
        } catch (IOException e) {
            log.error("Error while crawling URL: {}", url, e);
            throw e;
        }
        return extractedContent;
    }

    public void storeContent(List<String> contentList) {
        if (contentList == null || contentList.isEmpty()) {
            log.warn("Received an empty content list for storage.");
            return;
        }
        try {
            jdbcTemplate.update("delete from vector_store");
            log.info("Storing {} content items into the vector database.", contentList.size());
            List<Document> documents = contentList.stream()
                    .map(Document::new)
                    .collect(Collectors.toList());
            vectorStore.add(documents);
            log.info("Successfully stored content into the vector database.");
        } catch (Exception e) {
            log.error("Error occurred while storing content into the vector database: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to store content into the vector database.", e);
        }
    }

    public String queryContent(String query) {
        try {
            // Search for similar content in the vector database
            log.info("Searching for similar content in the vector database for query: {}", query);
            Document queryDocument = new Document(query);
            List<Document> similarDocuments = vectorStore.similaritySearch(String.valueOf(queryDocument));
            // Convert the documents to a list of strings
            List<String> results = similarDocuments.stream()
                    .map(Document::getContent)
                    .collect(Collectors.toList());
            log.info("Found {} similar content items for the query.", results.size());
            // Combine retrieved content into a single prompt
            String documents = String.join("\n", results);
            String template = """
                Using the content retrieved from the website, respond to the query.
                If the information is not found in the DOCUMENTS,
                clearly state: "The data is not available in the provided document."

                DOCUMENTS:
                {documents}
                QUERY:
                {query}
                """;
            String formattedPrompt = template.replace("{documents}", documents).replace("{query}", query);
            // Generate response using ChatGPT
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