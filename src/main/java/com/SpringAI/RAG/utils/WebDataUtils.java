package com.SpringAI.RAG.utils;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.Optional;

public class WebDataUtils {

    private static final Logger log = LoggerFactory.getLogger(WebDataUtils.class);
    private static final Pattern PHONE_PATTERN = Pattern.compile("(\\+\\d{1,3}[-.\\s]?)?(\\(?\\d{1,4}\\)?[-.\\s]?)?(\\d{1,4}[-.\\s]?){1,3}\\d{1,4}(\\s?x\\d+)?");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(\\.[a-zA-Z]{2,})?");

    // Validates the URL format
    public static boolean isValidUrl(String url) {
        try {
            return url != null && !url.isEmpty() && (url.startsWith("http://") || url.startsWith("https://"));
        } catch (Exception e) {
            log.error("Error while validating URL: {}. Message: {}", url, e.getMessage());
            return false;
        }
    }

    // Checks if the URL should be skipped based on file type
    public static boolean shouldSkipUrl(String url) {
        try {
            return url.matches(".*\\.(jpg|jpeg|png|gif|bmp|mp4|webm|mp3|wav|ogg|flac|avi|mov|wmv|mkv|pdf|docx|pptx|xlsx)$");
        } catch (Exception e) {
            log.error("Error while checking if URL should be skipped: {}. Message: {}", url, e.getMessage());
            return false;
        }
    }

    // Fetches URL with retry logic, handles exceptions and logs errors
    public static Optional<org.jsoup.nodes.Document> fetchUrlWithRetries(String url, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                org.jsoup.nodes.Document doc = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                        .timeout(10000)
                        .get();
                return Optional.of(doc);
            } catch (IOException e) {
                log.warn("Attempt {} failed for URL: {}, Message: {}", attempt, url, e.getMessage());
                if (attempt == maxRetries) {
                    log.error("Max retries reached for URL: {}", url);
                    return Optional.empty();
                }
                try {
                    // Exponential backoff
                    Thread.sleep((long) Math.pow(2, attempt) * 1000);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    log.error("Retry interrupted for URL: {}, Message:{}", url, interruptedException.getMessage());
                    break;
                }
            } catch (Exception e) {
                log.error("Unexpected error while fetching URL: {}, Message: {}", url, e.getMessage());
                break;
            }
        }
        return Optional.empty();
    }

    // Aggregates content from headers and their sections, logs progress
    public static void aggregateContent(org.jsoup.nodes.Document doc, Set<String> extractedContent) {
        try {
            Elements headers = doc.select("h1, h2, h3");
            for (Element header : headers) {
                String section = header.text();
                String content = extractSectionContent(header);
                if (!content.isEmpty()) {
                    extractedContent.add(section + ":\n" + content);
                    log.info("Extracted content from section: {}", section);
                }
            }
        } catch (Exception e) {
            log.error("Error while aggregating content from document: {}", e.getMessage());
        }
    }

    // Extracts content following a header until the next header
    public static String extractSectionContent(Element header) {
        try {
            StringBuilder sectionContent = new StringBuilder();
            Element sibling = header.nextElementSibling();
            while (sibling != null && !sibling.tagName().matches("h1|h2|h3")) {
                sectionContent.append(sibling.text()).append("\n");
                sibling = sibling.nextElementSibling();
            }
            return sectionContent.toString().trim();
        } catch (Exception e) {
            log.error("Error while extracting section content from header: {}", e.getMessage());
            return "";
        }
    }

    // Extracts special content like phone numbers, emails, and specific links
    public static void extractSpecialContent(String text, Set<String> extractedContent, org.jsoup.nodes.Document doc) {
        try {
            Map<String, String> linkPatterns = Map.of(
                    "GitHub link", "https?://(www\\.)?github\\.com/[a-zA-Z0-9_-]+(/[a-zA-Z0-9._-]+)*(\\?[a-zA-Z0-9=&%]+)?",
                    "LinkedIn link", "https?://(www\\.)?linkedin\\.com/(in/[a-zA-Z0-9_-]+(/[a-zA-Z0-9._-]+)*)|(company/[a-zA-Z0-9_-]+(/[a-zA-Z0-9._-]+)*)",
                    "Social Media link", "https?://(www\\.)?(facebook|twitter|instagram|pinterest)\\.com/[a-zA-Z0-9_-]+",
                    "Job Posting", "https?://(www\\.)?linkedin\\.com/jobs/view/\\d+",
                    "Academic Publication", "https?://(www\\.)?researchgate\\.net/publication/\\d+",
                    "Portfolio link", "https?://(www\\.)?([a-zA-Z0-9_-]+\\.)+[a-zA-Z]{2,6}/(portfolio|projects|work|my-work|my-projects)/?[a-zA-Z0-9_-]*"
            );

            // Extract phone numbers and emails
            extractWithPattern(text, PHONE_PATTERN, "Phone number", extractedContent);
            extractWithPattern(text, EMAIL_PATTERN, "Email", extractedContent);

            // Extract links matching predefined patterns
            doc.select("a[href]").stream()
                    .map(link -> link.attr("href"))
                    .forEach(href -> linkPatterns.forEach((label, pattern) -> {
                        if (href.matches(pattern)) {
                            extractedContent.add(label + ": " + href);
                            log.info("Extracted link: " + label + ": " + href);
                        }
                    }));
        } catch (Exception e) {
            log.error("Error while extracting special content: {}", e.getMessage());
        }
    }

    // Extracts content matching a pattern
    public static void extractWithPattern(String text, Pattern pattern, String label, Set<String> extractedContent) {
        try {
            var matcher = pattern.matcher(text);
            while (matcher.find()) {
                extractedContent.add(label + ": " + matcher.group());
                log.info("Extracted " + label + ": " + matcher.group());
            }
        } catch (Exception e) {
            log.error("Error while extracting content with pattern {}: {}", label, e.getMessage());
        }
    }

    public static String processJavaScriptPage(String url) {
        log.info("Processing JavaScript-heavy page: {}", url);
        // Set up WebDriverManager
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless", "--disable-gpu", "--no-sandbox");
        WebDriver driver = null;
        try {
            driver = new ChromeDriver(options);
            driver.get(url);
            String pageSource = driver.getPageSource();
            Document document = Jsoup.parse(pageSource);
            // Aggregate meaningful content
            Set<String> extractedContent = new HashSet<>();
            aggregateContent(document, extractedContent);
            // Combine extracted content into a single string
            return String.join("\n\n", extractedContent);
        } catch (Exception e) {
            log.error("Error processing JavaScript-heavy page: {}", url, e);
            return "";
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    log.error("Error shutting down WebDriver for URL: {}", url, e);
                }
            }
        }
    }
}
