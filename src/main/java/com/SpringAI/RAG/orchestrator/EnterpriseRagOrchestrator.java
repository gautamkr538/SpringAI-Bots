package com.SpringAI.RAG.orchestrator;

import com.SpringAI.RAG.dto.*;
import com.SpringAI.RAG.service.*;
import com.SpringAI.RAG.utils.AuditLogService;
import com.SpringAI.RAG.utils.FeedbackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class EnterpriseRagOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(EnterpriseRagOrchestrator.class);

    private final EnhancedChatService chatService;
    private final AuditLogService auditLogService;
    private final FeedbackService feedbackService;

    public EnterpriseRagOrchestrator(
            EnhancedChatService chatService,
            AuditLogService auditLogService,
            FeedbackService feedbackService
    ) {
        this.chatService = chatService;
        this.auditLogService = auditLogService;
        this.feedbackService = feedbackService;
    }

    public EnhancedChatResponse runChatWorkflow(String question, String userId, String userRole) {
        String sessionId = generateSessionId(userId);
        auditLogService.log(AuditLogEntry.moderation(
                "User moderation check initiated",
                Map.of("userId", userId, "userRole", userRole, "question", question)
        ));

        EnhancedChatResponse response = null;
        try {
            response = chatService.chat(question);

            auditLogService.log(AuditLogEntry.search(
                    "Document similarity search completed",
                    Map.of("question", question, "confidenceScore", response.getConfidenceScore())
            ));

            auditLogService.log(AuditLogEntry.synthesis(
                    "Chat answer synthesized",
                    Map.of("answer", response.getAnswerSafe())
            ));

            for (Citation citation : response.getCitationsSafe()) {
                auditLogService.log(AuditLogEntry.citation(
                        "Citation used in response",
                        Map.of("citation", citation)
                ));
            }

        } catch (Exception ex) {
            auditLogService.log(AuditLogEntry.exception(
                    "Error during chat workflow: " + ex.getMessage(),
                    Map.of("error", ex)
            ));
            log.error("Workflow processing failed for session {}: {}", sessionId, ex.getMessage(), ex);

            response = EnhancedChatResponse.builder()
                    .sessionId(sessionId)
                    .answer("[Error in chat workflow: " + ex.getMessage() + "]")
                    .build();
        }

        return response;
    }

    // Example: called from UI or API endpoint
    public void submitFeedback(String sessionId, String answerId, String feedbackText, Integer rating,
                              String userId, String userRole, String documentId) {

        FeedbackRecord feedback = FeedbackRecord.builder()
                .answerId(answerId)
                .feedbackText(feedbackText)
                .rating(rating)
                .userId(userId)
                .userRole(userRole)
                .documentId(documentId)
                .metadata(Map.of())
                .submittedAt(LocalDateTime.now())
                .build();

        feedbackService.submitFeedback(feedback);
        auditLogService.log(AuditLogEntry.feedback(
                feedbackText,
                Map.of("answerId", answerId, "userId", userId, "rating", rating)
        ));
        log.info("Feedback recorded for session {}: {}", sessionId, feedbackText);
    }

    // Util: Generate sessionId with user traceability
    private String generateSessionId(String userId) {
        return (userId != null ? userId : "anon") + "-" + System.currentTimeMillis();
    }
}
