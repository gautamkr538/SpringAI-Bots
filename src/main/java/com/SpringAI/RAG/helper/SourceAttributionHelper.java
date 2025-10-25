package com.SpringAI.RAG.helper;

import org.springframework.stereotype.Component;

@Component
public class SourceAttributionHelper {

    public String determineAttribution(String answer) {
        String lower = answer.toLowerCase();
        
        boolean hasDoc = lower.contains("passage") || lower.contains("based on the documents");
        boolean hasKnowledge = lower.contains("knowledge base") || lower.contains("don't have specific information");
        
        if (hasDoc && hasKnowledge) return "mixed";
        if (hasKnowledge) return "general-knowledge";
        return "document-based";
    }
}
