package com.SpringAI.RAG.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnhancedChatResponse {
    private String answer;
    private List<Citation> citations;
    private Double confidenceScore;
    private String sourceAttribution; // "document-based", "general-knowledge", "mixed", "no-match"
    private List<String> suggestedQuestions;
    private String followUpPrompt;
    private LocalDateTime timestamp;
    private String sessionId;

    public List<Citation> getCitationsSafe() {
        return citations != null ? citations : Collections.emptyList();
    }

    public List<String> getSuggestedQuestionsSafe() {
        return suggestedQuestions != null ? suggestedQuestions : Collections.emptyList();
    }

    public String getAnswerSafe() {
        return answer != null ? answer : "[No answer generated]";
    }
}