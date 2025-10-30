package com.SpringAI.RAG.orchestrator;

import com.SpringAI.RAG.dto.*;
import com.SpringAI.RAG.service.EnhancedChatService;
import com.SpringAI.RAG.utils.AuditLogService;
import com.SpringAI.RAG.utils.FeedbackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

@Component
public class SpecialTaskOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SpecialTaskOrchestrator.class);

    private final EnhancedChatService chatService;
    private final AuditLogService auditLogService;
    private final FeedbackService feedbackService;

    public SpecialTaskOrchestrator(EnhancedChatService chatService,
                                   AuditLogService auditLogService,
                                   FeedbackService feedbackService) {
        this.chatService = chatService;
        this.auditLogService = auditLogService;
        this.feedbackService = feedbackService;
    }

    public SpecialTaskResponse handleSpecialTask(SpecialTaskRequest req, String userId, String userRole) {
        String sessionId = generateSessionId(userId);

        if (req == null || req.getTaskType() == null || req.getTaskType().isEmpty()) {
            log.error("[{}] SpecialTaskRequest missing type.", sessionId);
            auditLogService.log(AuditLogEntry.exception("SpecialTaskRequest missing type", null));
            return SpecialTaskResponse.builder()
                    .taskType("error")
                    .result("[Error: Task type missing]")
                    .generatedAt(LocalDateTime.now())
                    .metadata(Collections.emptyMap())
                    .build();
        }

        auditLogService.log(AuditLogEntry.search("Special task requested: " + req.getTaskType(), req.getParametersSafe()));

        String result = null;
        Map<String, Object> meta = req.getParametersSafe();
        try {
            switch (req.getTaskType().toLowerCase()) {
                case "summary":
                    result = generateDocumentSummary(req.getContext());
                    auditLogService.log(AuditLogEntry.synthesis("Summary generated", Map.of("context", req.getContext())));
                    break;
                case "compliance":
                    result = runDocumentComplianceCheck(req.getContext());
                    auditLogService.log(AuditLogEntry.synthesis("Compliance check completed", Map.of("context", req.getContext())));
                    break;
                case "faq":
                    result = generateFAQs(req.getContext());
                    auditLogService.log(AuditLogEntry.synthesis("FAQ generation completed", Map.of("context", req.getContext())));
                    break;
                case "analyze":
                    result = customAnalyze(req.getContext(), req.getParametersSafe());
                    auditLogService.log(AuditLogEntry.synthesis("Custom analysis completed", Map.of("context", req.getContext())));
                    break;
                default:
                    result = "[Task unknown: " + req.getTaskType() + "]";
                    auditLogService.log(AuditLogEntry.fallback("Unknown special task type", null));
            }
        } catch (Exception ex) {
            log.error("[{}] SpecialTask execution failed: {}", sessionId, ex.getMessage());
            auditLogService.log(AuditLogEntry.exception("SpecialTask execution error", Map.of("error", ex.getMessage())));
            result = "[Error executing task: " + ex.getMessage() + "]";
        }

        // Feedback hook - can trigger at summary/compliance/FAQ completion
        feedbackService.submitFeedback(FeedbackRecord.compliance(
                sessionId,
                req.getContext(),
                userId,
                userRole,
                "Completed: " + req.getTaskType(),
                Map.of("result", result)
        ));

        return SpecialTaskResponse.builder()
                .taskType(req.getTaskType())
                .result(result)
                .generatedAt(LocalDateTime.now())
                .metadata(meta)
                .build();
    }

    private String generateDocumentSummary(String context) {
        // TODO: Add call to AI document summary service
        return "[Summary for context: " + context + "]";
    }

    private String runDocumentComplianceCheck(String context) {
        // TODO: Add compliance rules, scan document, return summary
        return "[Compliance report for context: " + context + "]";
    }

    private String generateFAQs(String context) {
        // TODO: Integrate with QuestionGenerationHelper or LLM
        return "[Generated FAQs for: " + context + "]";
    }

    private String customAnalyze(String context, Map<String, Object> params) {
        // TODO: Add domain-specific logic, stats, keyword extraction, etc.
        return "[Analysis of " + context + " with parameters: " + params + "]";
    }

    private String generateSessionId(String userId) {
        return (userId != null ? userId : "anon") + "-" + System.currentTimeMillis();
    }
}
