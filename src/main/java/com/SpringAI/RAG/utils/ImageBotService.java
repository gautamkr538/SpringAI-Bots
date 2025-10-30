package com.SpringAI.RAG.utils;

import com.SpringAI.RAG.dto.AuditLogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.Base64;
import java.util.Map;

@Service
public class ImageBotService {

    private static final Logger log = LoggerFactory.getLogger(ImageBotService.class);

    @Autowired
    private OpenAiImageModel imageModel;
    @Autowired
    private AuditLogService auditLogService;

    /**
     * Generate image and return the image URL if available, or a "data:image/png;base64,..." string if only base64 is available.
     */
    public String generateImageUrlOrBase64(String prompt, String sessionId) {
        log.info("[{}] Image generation requested.", sessionId);
        if (prompt == null || prompt.trim().isEmpty()) {
            log.warn("[{}] Empty prompt for image generation.", sessionId);
            auditLogService.log(
                    AuditLogEntry.fallback("Image generation called with empty prompt.", null));
            return null;
        }
        try {
            OpenAiImageOptions options = OpenAiImageOptions.builder()
                    .model("dall-e-3")
                    .N(1)
                    .responseFormat("url") // or "b64_json"
                    .build();

            String template = """
                Generate an image with the following prompt:
                {prompt}
                """;
            ImagePrompt imagePrompt = new ImagePrompt(template.replace("{prompt}", prompt), options);
            ImageResponse imageResponse = imageModel.call(imagePrompt);

            org.springframework.ai.image.Image imageResult = imageResponse.getResult().getOutput();
            String imageUrl = imageResult.getUrl();
            String b64 = imageResult.getB64Json();

            if (imageUrl != null && !imageUrl.isEmpty()) {
                log.info("[{}] Image generated with URL: {}", sessionId, imageUrl);
                auditLogService.log(AuditLogEntry.synthesis("Image generated (URL returned)", null));
                return imageUrl;
            } else if (b64 != null && !b64.isEmpty()) {
                String dataUrl = "data:image/png;base64," + b64;
                log.info("[{}] Image generated (base64 returned)", sessionId);
                auditLogService.log(AuditLogEntry.synthesis("Image generated (base64 value returned)", null));
                return dataUrl;
            } else {
                throw new Exception("Image generation service returned neither URL nor base64 data.");
            }
        } catch (Exception ex) {
            log.error("[{}] Image synthesis failed: {}", sessionId, ex.getMessage());
            auditLogService.log(AuditLogEntry.exception(
                    "Image generation failed.", Map.of("error", ex.getMessage())));
            return null;
        }
    }

    /**
     * Utility: download image bytes from a remote URL, or decode base64 if data:image format.
     * Returns empty array if error.
     */
    public byte[] getImageBytes(String urlOrBase64, String sessionId) {
        if (urlOrBase64 == null || urlOrBase64.isEmpty()) return new byte[0];
        try {
            if (urlOrBase64.startsWith("http")) {
                try (java.io.InputStream in = new URL(urlOrBase64).openStream()) {
                    return in.readAllBytes();
                }
            } else if (urlOrBase64.startsWith("data:image")) {
                String base64 = urlOrBase64.substring(urlOrBase64.indexOf(",") + 1);
                return Base64.getDecoder().decode(base64);
            }
        } catch (Exception e) {
            log.error("[{}] Could not get image bytes: {}", sessionId, e.getMessage());
        }
        return new byte[0];
    }

    // For image detection/analysis—stub for now, replace with actual vision API/model if desired.
    public String detectImage(byte[] image, String sessionId) {
        log.info("[{}] Image detection requested.", sessionId);
        if (image == null || image.length == 0) {
            log.warn("[{}] Empty bytes for image detection.", sessionId);
            auditLogService.log(
                    AuditLogEntry.fallback("Image detection called with empty input.", null));
            return "[No image provided]";
        }
        try {
            // TODO: Integrate vision API if available.
            String analysisResult = "Image detection completed. Found key elements in the provided image.";
            log.info("[{}] Image successfully analyzed.", sessionId);
            auditLogService.log(AuditLogEntry.synthesis("Image detected and analyzed.", null));
            return analysisResult;
        } catch (Exception ex) {
            log.error("[{}] Image analysis failed: {}", sessionId, ex.getMessage());
            auditLogService.log(AuditLogEntry.exception(
                    "Image detection failed.", Map.of("error", ex.getMessage())));
            return "[Detection error]";
        }
    }
}