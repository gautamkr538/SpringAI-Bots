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
public class FeedbackRecord {

    private String answerId;          // could be citation ID, response ID, etc.
    private String feedbackText;      // e.g. "Was this helpful?", "Needs more detail"
    private Integer rating;           // optional (1–5 scale)
    private LocalDateTime submittedAt;
    private Map<String, Object> metadata;

    private String userId;
    private String userRole;
    private String documentId;

    public String getFeedbackTextSafe() {
        return feedbackText != null ? feedbackText : "[No feedback provided]";
    }

    public static FeedbackRecord positive(String sessionId, String answerId, String userId, String userRole, Map<String, Object> metadata) {
        return create(answerId, "positive", 5, userId, userRole, null, metadata);
    }

    public static FeedbackRecord negative(String sessionId, String answerId, String userId, String userRole, Map<String, Object> metadata) {
        return create(answerId, "negative", 1, userId, userRole, null, metadata);
    }

    public static FeedbackRecord neutral(String sessionId, String answerId, String userId, String userRole, Map<String, Object> metadata) {
        return create(answerId, "neutral", 3, userId, userRole, null, metadata);
    }

    public static FeedbackRecord compliance(String sessionId, String documentId, String userId, String userRole, String feedback, Map<String, Object> metadata) {
        return create(null, feedback, 4, userId, userRole, documentId, metadata);
    }

    public static FeedbackRecord voiceCall(String sessionId, String feedback, String userId, String userRole, Map<String, Object> metadata) {
        return create(null, "voice-call: " + feedback, null, userId, userRole, null, metadata);
    }

    public static FeedbackRecord imageDetection(String sessionId, String feedback, String userId, String userRole, Map<String, Object> metadata) {
        return create(null, "image-detection: " + feedback, null, userId, userRole, null, metadata);
    }

    private static FeedbackRecord create(String answerId,
                                         String feedbackText,
                                         Integer rating,
                                         String userId,
                                         String userRole,
                                         String documentId,
                                         Map<String, Object> metadata) {

        return FeedbackRecord.builder()
                .answerId(answerId)
                .feedbackText(feedbackText)
                .rating(rating)
                .submittedAt(LocalDateTime.now())
                .userId(userId)
                .userRole(userRole)
                .documentId(documentId)
                .metadata(metadata)
                .build();
    }
}