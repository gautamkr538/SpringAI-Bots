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
public class AuditLogEntry {

    private String stepType; // e.g. "moderation", "search", "synthesis", "feedback", "fallback", "exception", "citation"
    private String detail;
    private LocalDateTime timestamp;
    private Map<String, Object> metadata;

    public static AuditLogEntry moderation(String detail, Map<String, Object> metadata) {
        return create("moderation", detail, metadata);
    }

    public static AuditLogEntry feedback(String feedback, Map<String, Object> metadata) {
        return create("feedback", feedback, metadata);
    }

    public static AuditLogEntry search(String detail, Map<String, Object> metadata) {
        return create("search", detail, metadata);
    }

    public static AuditLogEntry synthesis(String detail, Map<String, Object> metadata) {
        return create("synthesis", detail, metadata);
    }

    public static AuditLogEntry fallback(String detail, Map<String, Object> metadata) {
        return create("fallback", detail, metadata);
    }

    public static AuditLogEntry exception(String detail, Map<String, Object> metadata) {
        return create("exception", detail, metadata);
    }

    public static AuditLogEntry citation(String detail, Map<String, Object> metadata) {
        return create("citation", detail, metadata);
    }

    private static AuditLogEntry create(String type, String detail, Map<String, Object> metadata) {
        return AuditLogEntry.builder()
                .stepType(type)
                .detail(detail)
                .timestamp(LocalDateTime.now())
                .metadata(metadata)
                .build();
    }
}
