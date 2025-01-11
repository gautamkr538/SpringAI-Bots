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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    // Crawl and extract content from the provided URL
    public List<String> crawlAndExtractContent(String url) {
        Set<String> visitedLinks = new HashSet<>();
        Set<String> extractedContent = new HashSet<>();
        crawlAndExtractHelper(url, visitedLinks, extractedContent);
        return new ArrayList<>(extractedContent);
    }

    // Helper method for crawling and extracting content from the URL
    private void crawlAndExtractHelper(String url, Set<String> visitedLinks, Set<String> extractedContent) {
        if (visitedLinks.contains(url)) {
            return;
        }
        visitedLinks.add(url);
        // Skip URLs that match certain patterns
        if (shouldSkipUrl(url)) {
            log.info("Skipping URL: {} because it matches an excluded domain or media type.", url);
            extractedContent.add(url);
            return;
        }
        try {
            // Parse the content of the URL using Jsoup
            org.jsoup.nodes.Document doc = Jsoup.connect(url)
                    .timeout(10000)
                    .userAgent("Mozilla/5.0")
                    .execute()
                    .parse();
            String bodyText = doc.body().text();
            extractSpecialContent(bodyText, extractedContent, doc);
            // Add body text to the extracted content
            if (!bodyText.isEmpty()) {
                log.info("Extracted Content: {}", bodyText);
                extractedContent.add(bodyText);
            }
            // Extract and crawl links from the page
            Elements links = doc.select("a[href]");
            for (Element link : links) {
                String nextLink = link.absUrl("href");
                // Recursively crawl valid links
                if ((nextLink.startsWith("http://") || nextLink.startsWith("https://")) && !visitedLinks.contains(nextLink)) {
                    crawlAndExtractHelper(nextLink, visitedLinks, extractedContent);
                }
            }
        }
        catch (org.jsoup.HttpStatusException e) {
            log.warn("HTTP error (Status Code: {}) while crawling URL: {}. Message: {}. Skipping...", e.getStatusCode(), url, e.getMessage());
            extractedContent.add(url);
        } catch (org.jsoup.UnsupportedMimeTypeException e) {
            log.warn("Unsupported MIME type for URL: {}. Skipping...", url);
            extractedContent.add(url);
        } catch (Exception e) {
            log.error("Error while crawling URL: {}. Message: {}", url, e.getMessage());
            extractedContent.add(url);
        }
    }

    // Extract special content like GitHub, LinkedIn links, phone numbers, and emails
    private void extractSpecialContent(String text, Set<String> extractedContent, org.jsoup.nodes.Document doc) {
        String githubPattern = "https://(www\\.)?github\\.com/([a-zA-Z0-9_-]+)";
        String linkedinPattern = "https://(www\\.)?linkedin\\.com/in/([a-zA-Z0-9_-]+)";
        extractPhoneNumbers(text, extractedContent);
        extractEmails(text, extractedContent);

        // Extract and add GitHub links to content
        Elements githubLinks = doc.select("a[href~=" + githubPattern + "]");
        for (Element link : githubLinks) {
            extractedContent.add("GitHub link: " + link.attr("href"));
        }
        // Extract and add LinkedIn links to content
        Elements linkedinLinks = doc.select("a[href~=" + linkedinPattern + "]");
        for (Element link : linkedinLinks) {
            extractedContent.add("LinkedIn link: " + link.attr("href"));
        }
    }

    private boolean shouldSkipUrl(String url) {
        // Skip if the URL is pointing to email, GitHub, LinkedIn, media files (image/video/audio)
        return url.matches("^(mailto:|.*(github|linkedin)\\.com.*)$") ||
                url.matches(".*\\.(jpg|jpeg|png|gif|bmp|mp4|webm|mp3|wav|ogg|flac|avi|mov|wmv|mkv)$");
    }

    // Extract phone numbers from the text
    private void extractPhoneNumbers(String text, Set<String> extractedContent) {
        Pattern phonePattern = Pattern.compile("(\\+\\d{1,2}\\s?)?\\(?\\d{3}\\)?\\s?\\d{3}-?\\d{4}");
        Matcher phoneMatcher = phonePattern.matcher(text);
        while (phoneMatcher.find()) {
            extractedContent.add("Phone number: " + phoneMatcher.group());
        }
    }

    // Extract email addresses from the text
    private void extractEmails(String text, Set<String> extractedContent) {
        Pattern emailPattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
        Matcher emailMatcher = emailPattern.matcher(text);
        while (emailMatcher.find()) {
            extractedContent.add("Email: " + emailMatcher.group());
        }
    }

    // Store the extracted content into the vector database
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
                    log.info("Storing {} content items into the vector database.", batchDocuments.size());
                    vectorStore.add(batchDocuments);
                    batchDocuments.clear();
                }
            }
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
            // Format documents for prompt and send to ChatGPT for response
            String documents = similarDocuments.stream()
                    .map(Document::getContent)
                    .collect(Collectors.joining("\n"));
            String template = """
            Based on the DOCUMENTS below, respond to the QUERY.
            If the answer is not available, state: "The data is not available in the provided document."

            DOCUMENTS:
            {documents}

            QUERY:
            {query}
            """;
            // Format the prompt for ChatGPT
            String formattedPrompt = template.replace("{documents}", documents).replace("{query}", query);
            SystemMessage systemMessage = new SystemMessage(formattedPrompt);
            UserMessage userMessage = new UserMessage(query);
            Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
            // Get response from the chat client
            return chatClient.prompt(prompt).call().content();
        } catch (Exception e) {
            log.error("Error occurred while processing the query: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process the query.", e);
        }
    }
}