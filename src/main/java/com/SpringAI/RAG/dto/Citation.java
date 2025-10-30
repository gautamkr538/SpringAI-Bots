package com.SpringAI.RAG.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Citation {
    private String passageText;
    private String documentSource;
    private Integer pageNumber;
    private Double relevanceScore;
    private Map<String, Object> metadata;

    public String getPassageTextSafe() {
        return passageText != null ? passageText : "[No passage text]";
    }

    public String getDocumentSourceSafe() {
        return documentSource != null ? documentSource : "[No source]";
    }

    public Double getRelevanceScoreSafe() {
        return relevanceScore != null ? relevanceScore : 0.0;
    }

    public Map<String, Object> getMetadataSafe() {
        return metadata != null ? metadata : Collections.emptyMap();
    }
}