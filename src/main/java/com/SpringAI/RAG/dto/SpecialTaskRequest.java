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
public class SpecialTaskRequest {
    private String taskType; // "summary", "compliance", "faq", "analyze"
    private String context;
    private Map<String, Object> parameters;

    public Map<String, Object> getParametersSafe() {
        return parameters != null ? parameters : Collections.emptyMap();
    }
}