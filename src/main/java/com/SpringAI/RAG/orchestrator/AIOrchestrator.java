package com.SpringAI.RAG.orchestrator;

import com.SpringAI.RAG.dto.*;
import com.SpringAI.RAG.service.*;
import org.springframework.stereotype.Component;

@Component
public class AIOrchestrator {

    private final EnhancedChatService chatService;

    public AIOrchestrator(EnhancedChatService chatService) {this.chatService = chatService;}

    // Delegate to appropriate service
    public EnhancedChatResponse chat(String question) {
        return chatService.chat(question);
    }
}