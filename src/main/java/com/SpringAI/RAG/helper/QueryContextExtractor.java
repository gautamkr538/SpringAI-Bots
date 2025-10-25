package com.SpringAI.RAG.helper;

import com.SpringAI.RAG.dto.QueryContext;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class QueryContextExtractor {

    private static final Logger log = LoggerFactory.getLogger(QueryContextExtractor.class);

    private final ChatClient chatClient;

    public QueryContextExtractor(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public QueryContext extractContext(String userQuery) {
        log.debug("Extracting query context from: '{}'", userQuery);

        String template = """
                You are a query analysis expert that extracts structured metadata from user questions.
                
                TASK: Analyze the USER QUERY and extract relevant metadata for document search filtering.
                
                Extract the following if present:
                - category: The general topic/domain (e.g., technical, legal, financial, medical, general)
                - time_period: Any time-related context (e.g., recent, 2024, last quarter, historical)
                - entity_type: Type of entity mentioned (e.g., person, company, product, location)
                - document_type: Type of document implied (e.g., report, manual, policy, guide)
                - priority: Urgency level (high, medium, low, normal)
                - specificity: How specific the query is (specific, general, broad)
                
                OUTPUT FORMAT (JSON):
                {
                    "category": "value or null",
                    "time_period": "value or null",
                    "entity_type": "value or null",
                    "document_type": "value or null",
                    "priority": "value or null",
                    "specificity": "value or null",
                    "key_terms": ["term1", "term2"],
                    "search_intent": "informational|navigational|transactional"
                }
                
                Return ONLY valid JSON without markdown code blocks. If a field is not applicable, use null.
                
                USER QUERY:
                {query}
                """;

        try {
            PromptTemplate pt = new PromptTemplate(template);
            var prompt = pt.createMessage(Map.of("query", userQuery));

            String jsonResponse = chatClient.prompt(new Prompt(List.of(prompt))).call().content();
            QueryContext context = parseQueryContext(jsonResponse, userQuery);

            log.debug("Extracted context: category={}, specificity={}, key_terms={}",
                    context.getCategory(),
                    context.getSpecificity(),
                    context.getKeyTerms() != null ? context.getKeyTerms().size() : 0);

            return context;

        } catch (Exception e) {
            log.error("Failed to extract query context, using defaults", e);
            return createDefaultContext(userQuery);
        }
    }

    private QueryContext parseQueryContext(String jsonResponse, String originalQuery) {
        try {
            String cleaned = jsonResponse.trim();

            // Clean markdown-style code blocks if present
            if (cleaned.startsWith("```json")) {
                cleaned = cleaned.substring(7).trim();
            } else if (cleaned.startsWith("```")) {
                cleaned = cleaned.substring(3).trim();
            }

            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
            }

            log.debug("Cleaned JSON: {}", cleaned);

            return QueryContext.builder()
                    .originalQuery(originalQuery)
                    .category(extractJsonValue(cleaned, "category"))
                    .timePeriod(extractJsonValue(cleaned, "time_period"))
                    .entityType(extractJsonValue(cleaned, "entity_type"))
                    .documentType(extractJsonValue(cleaned, "document_type"))
                    .priority(extractJsonValue(cleaned, "priority"))
                    .specificity(extractJsonValue(cleaned, "specificity"))
                    .keyTerms(extractJsonArray(cleaned, "key_terms"))
                    .searchIntent(extractJsonValue(cleaned, "search_intent"))
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse JSON context: {}", e.getMessage());
            return createDefaultContext(originalQuery);
        }
    }

    private QueryContext createDefaultContext(String query) {
        log.debug("Creating default context for query: '{}'", query);

        return QueryContext.builder()
                .originalQuery(query)
                .category("general")
                .specificity("general")
                .priority("normal")
                .searchIntent("informational")
                .keyTerms(Arrays.asList(query.split("\\s+")))
                .build();
    }

    private String extractJsonValue(String json, String key) {
        try {
            String pattern = "\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]+)\"";
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(json);

            if (m.find()) {
                String value = m.group(1);
                return "null".equalsIgnoreCase(value) ? null : value;
            }

            // handle null explicitly
            String nullPattern = "\"" + Pattern.quote(key) + "\"\\s*:\\s*null";
            Pattern np = Pattern.compile(nullPattern);
            if (np.matcher(json).find()) {
                return null;
            }

            return null;
        } catch (Exception e) {
            log.debug("Failed to extract '{}': {}", key, e.getMessage());
            return null;
        }
    }

    private List<String> extractJsonArray(String json, String key) {
        try {
            String pattern = "\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[(.*?)\\]";
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(json);

            if (m.find()) {
                String arrayContent = m.group(1);
                List<String> values = new ArrayList<>();
                Pattern valuePattern = Pattern.compile("\"([^\"]+)\"");
                Matcher valueMatcher = valuePattern.matcher(arrayContent);

                while (valueMatcher.find()) {
                    String value = valueMatcher.group(1);
                    if (!value.isEmpty() && !"null".equalsIgnoreCase(value)) {
                        values.add(value);
                    }
                }

                return values;
            }

            return Collections.emptyList();
        } catch (Exception e) {
            log.debug("Failed to extract array '{}': {}", key, e.getMessage());
            return Collections.emptyList();
        }
    }
}