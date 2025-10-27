package com.SpringAI.RAG.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}

