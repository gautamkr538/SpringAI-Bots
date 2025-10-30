package com.SpringAI.RAG.orchestrator;

import com.SpringAI.RAG.dto.EnhancedChatResponse;
import com.SpringAI.RAG.service.EnhancedChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AIOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AIOrchestrator.class);

    private final EnhancedChatService chatService;

    public AIOrchestrator(EnhancedChatService chatService) {
        this.chatService = chatService;
    }

    // Safe delegation, logging
    public EnhancedChatResponse chat(String question) {
        if (chatService == null) {
            log.error("No EnhancedChatService available for orchestration.");
            return EnhancedChatResponse.builder()
                    .answer("[Chat service unavailable]")
                    .build();
        }
        if (question == null || question.trim().isEmpty()) {
            log.warn("AIOrchestrator called with null or empty question.");
            return EnhancedChatResponse.builder()
                    .answer("[No question provided]")
                    .build();
        }
        log.info("Orchestrator forwarding question: '{}'", question);
        return chatService.chat(question);
    }
}