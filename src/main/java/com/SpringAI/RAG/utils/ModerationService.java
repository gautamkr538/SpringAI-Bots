package com.SpringAI.RAG.utils;

import com.SpringAI.RAG.config.ModerationThresholds;
import com.SpringAI.RAG.exception.ContentModerationException;
import com.SpringAI.RAG.exception.ChatServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.moderation.*;
import org.springframework.ai.openai.OpenAiModerationModel;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class ModerationService {

    private final OpenAiModerationModel moderationModel;
    private final ModerationThresholds thresholds;

    private record CategoryCheck(double score, double threshold) {
        public boolean isViolated() { return score > threshold; }
        public String getPercentage() { return String.format("%.2f%%", score * 100); }
    }

    /**
     * Throws ContentModerationException if flagged.
     */
    public void validate(String text) {
        log.info("Validating user input for content violations.");
        if (text == null || text.trim().isEmpty()) {
            log.error("Moderation check failed: message is empty or null.");
            throw new ChatServiceException("Message cannot be empty");
        }

        try {
            ModerationPrompt prompt = new ModerationPrompt(text.trim());
            ModerationResponse response = moderationModel.call(prompt);

            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                log.error("No result or output from moderation API call.");
                throw new ChatServiceException("Moderation API returned no result");
            }
            Moderation moderation = response.getResult().getOutput();
            ModerationResult result = moderation.getResults() != null && !moderation.getResults().isEmpty()
                    ? moderation.getResults().getFirst() : null;
            if (result == null) {
                log.error("ModerationResult is null; cannot validate content.");
                throw new ChatServiceException("No moderation result returned");
            }
            CategoryScores scores = result.getCategoryScores();
            if (scores == null) {
                log.error("No category scores in moderation result.");
                throw new ChatServiceException("No score details returned from moderation");
            }

            Map<String, CategoryCheck> checks = Map.ofEntries(
                    Map.entry("Hate", new CategoryCheck(scores.getHate(), thresholds.hate())),
                    Map.entry("Sexual", new CategoryCheck(scores.getSexual(), thresholds.sexual())),
                    Map.entry("Self-Harm", new CategoryCheck(scores.getSelfHarm(), thresholds.selfHarm())),
                    Map.entry("Violence", new CategoryCheck(scores.getViolence(), thresholds.violence())),
                    Map.entry("Harassment", new CategoryCheck(scores.getHarassment(), thresholds.harassment())),
                    Map.entry("SexualMinors", new CategoryCheck(scores.getSexualMinors(), thresholds.sexualMinors())),
                    Map.entry("HateThreatening", new CategoryCheck(scores.getHateThreatening(), thresholds.hateThreatening())),
                    Map.entry("ViolenceGraphic", new CategoryCheck(scores.getViolenceGraphic(), thresholds.violenceGraphic())),
                    Map.entry("SelfHarmIntent", new CategoryCheck(scores.getSelfHarmIntent(), thresholds.selfHarmIntent())),
                    Map.entry("SelfHarmInstructions", new CategoryCheck(scores.getSelfHarmInstructions(), thresholds.selfHarmInstructions())),
                    Map.entry("HarassmentThreatening", new CategoryCheck(scores.getHarassmentThreatening(), thresholds.harassmentThreatening()))
            );

            Map<String, Object> violations = new LinkedHashMap<>();
            boolean flagged = false;
            for (var entry : checks.entrySet()) {
                CategoryCheck check = entry.getValue();
                if (check.isViolated()) {
                    violations.put(entry.getKey(), Map.of(
                            "score", check.score(),
                            "threshold", check.threshold(),
                            "percentage", check.getPercentage()
                    ));
                    flagged = true;
                    log.warn("Content violation for {}: {} (>{})", entry.getKey(), check.score(), check.threshold());
                }
            }
            if (flagged) {
                String message = "Your message contains content that violates our community guidelines. Detected violations: "
                        + String.join(", ", violations.keySet()) + ". Please rephrase your question.";
                throw new ContentModerationException(message, violations, moderation.getId(), moderation.getModel());
            }
        } catch (ContentModerationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Moderation check failed: {}", e.getMessage(), e);
            throw new ChatServiceException("Moderation check failed", e);
        }
    }
}