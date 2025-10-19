package com.SpringAI.RAG.exception;

import lombok.Getter;
import java.util.Map;

@Getter
public class ContentModerationException extends RuntimeException {
    
    private final Map<String, Object> violations;
    private final String moderationId;
    private final String model;
    
    public ContentModerationException(String message, Map<String, Object> violations, String moderationId, String model) {
        super(message);
        this.violations = violations;
        this.moderationId = moderationId;
        this.model = model;
    }
}
