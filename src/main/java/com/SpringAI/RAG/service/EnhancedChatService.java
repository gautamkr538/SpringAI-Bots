package com.SpringAI.RAG.service;

import com.SpringAI.RAG.dto.EnhancedChatResponse;

public interface EnhancedChatService {
    EnhancedChatResponse chat(String question);
}
