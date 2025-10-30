package com.SpringAI.RAG.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SourceAttributionHelper {

    private static final Logger log = LoggerFactory.getLogger(SourceAttributionHelper.class);

    public String determineAttribution(String answer) {
        if (answer == null || answer.trim().isEmpty()) {
            log.warn("determineAttribution called with null/empty answer. Returning 'no-match'.");
            return "no-match";
        }
        String lower = answer.toLowerCase();

        boolean hasDoc = lower.contains("passage") || lower.contains("based on the documents");
        boolean hasKnowledge = lower.contains("knowledge base") || lower.contains("don't have specific information");
        String result;
        if (hasDoc && hasKnowledge) result = "mixed";
        else if (hasKnowledge) result = "general-knowledge";
        else if (hasDoc) result = "document-based";
        else result = "no-match";

        log.info("Source attribution determined as: {}", result);
        return result;
    }
}