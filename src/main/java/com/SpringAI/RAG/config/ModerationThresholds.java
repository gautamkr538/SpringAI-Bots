package com.SpringAI.RAG.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for content moderation thresholds.
 * Each threshold represents the minimum score (0.0 to 1.0) required to flag content.
 */
@ConfigurationProperties(prefix = "moderation.thresholds")
@Validated
public record ModerationThresholds(
        @DecimalMin(value = "0.0") @DecimalMax(value = "1.0") Double hate,
        @DecimalMin(value = "0.0") @DecimalMax(value = "1.0") Double sexual,
        @DecimalMin(value = "0.0") @DecimalMax(value = "1.0") Double selfHarm,
        @DecimalMin(value = "0.0") @DecimalMax(value = "1.0") Double violence,
        @DecimalMin(value = "0.0") @DecimalMax(value = "1.0") Double harassment,
        @DecimalMin(value = "0.0") @DecimalMax(value = "1.0") Double sexualMinors,
        @DecimalMin(value = "0.0") @DecimalMax(value = "1.0") Double hateThreatening,
        @DecimalMin(value = "0.0") @DecimalMax(value = "1.0") Double violenceGraphic,
        @DecimalMin(value = "0.0") @DecimalMax(value = "1.0") Double selfHarmIntent,
        @DecimalMin(value = "0.0") @DecimalMax(value = "1.0") Double selfHarmInstructions,
        @DecimalMin(value = "0.0") @DecimalMax(value = "1.0") Double harassmentThreatening
) {
    // Constructor with default values
    public ModerationThresholds {
        hate = hate != null ? hate : 0.8;
        sexual = sexual != null ? sexual : 0.7;
        selfHarm = selfHarm != null ? selfHarm : 0.6;
        violence = violence != null ? violence : 0.75;
        harassment = harassment != null ? harassment : 0.8;
        sexualMinors = sexualMinors != null ? sexualMinors : 0.5;
        hateThreatening = hateThreatening != null ? hateThreatening : 0.7;
        violenceGraphic = violenceGraphic != null ? violenceGraphic : 0.75;
        selfHarmIntent = selfHarmIntent != null ? selfHarmIntent : 0.6;
        selfHarmInstructions = selfHarmInstructions != null ? selfHarmInstructions : 0.5;
        harassmentThreatening = harassmentThreatening != null ? harassmentThreatening : 0.7;
    }
}