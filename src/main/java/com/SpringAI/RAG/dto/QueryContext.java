package com.SpringAI.RAG.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryContext {
    private String originalQuery;
    private String category;
    private String timePeriod;
    private String entityType;
    private String documentType;
    private String priority;
    private String specificity;
    private List<String> keyTerms;
    private String searchIntent;

    public boolean hasCategory() {
        return category != null && !category.isEmpty() && !"general".equalsIgnoreCase(category);
    }

    public boolean hasTimePeriod() {
        return timePeriod != null && !timePeriod.isEmpty();
    }

    public boolean hasDocumentType() {
        return documentType != null && !documentType.isEmpty();
    }

    public List<String> getKeyTermsSafe() {
        return keyTerms != null ? keyTerms : Collections.emptyList();
    }
}