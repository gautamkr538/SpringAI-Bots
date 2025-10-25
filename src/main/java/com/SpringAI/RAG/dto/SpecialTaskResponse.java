package com.SpringAI.RAG.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpecialTaskResponse {
    private String taskType;
    private String result;
    private LocalDateTime generatedAt;
    private Map<String, Object> metadata;
}
