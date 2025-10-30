package com.SpringAI.RAG.utils;

import com.SpringAI.RAG.dto.AuditLogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.audio.speech.SpeechPrompt;
import org.springframework.ai.openai.audio.speech.SpeechResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class VoiceBotService {

    private static final Logger log = LoggerFactory.getLogger(VoiceBotService.class);

    @Autowired
    private OpenAiAudioSpeechModel speechModel;
    @Autowired
    private AuditLogService auditLogService;

    // Text-to-Speech: Generate voice (MP3)
    public byte[] generateVoice(String text, String sessionId) {
        log.info("[{}] Voice generation requested.", sessionId);
        if (text == null || text.trim().isEmpty()) {
            log.warn("[{}] Empty text for voice generation.", sessionId);
            auditLogService.log(AuditLogEntry.fallback(
                    "Voice generation called with empty text.", null));
            return new byte[0];
        }
        try {
            OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
                    .model("tts-1-hd")
                    .voice(OpenAiAudioApi.SpeechRequest.Voice.ALLOY)
                    .responseFormat(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3)
                    .speed(1.0f)
                    .build();
            SpeechPrompt speechPrompt = new SpeechPrompt(text, options);
            SpeechResponse speechResponse = speechModel.call(speechPrompt);
            byte[] audioBytes = speechResponse.getResult().getOutput();

            if (audioBytes == null || audioBytes.length == 0) {
                throw new Exception("Voice generation service returned no audio data.");
            }
            log.info("[{}] Voice successfully generated.", sessionId);
            auditLogService.log(AuditLogEntry.synthesis(
                    "Voice generated for provided answer.", null));
            return audioBytes;
        } catch (Exception ex) {
            log.error("[{}] Voice synthesis failed: {}", sessionId, ex.getMessage());
            auditLogService.log(AuditLogEntry.exception(
                    "Voice generation failed.", Map.of("error", ex.getMessage())));
            return new byte[0];
        }
    }

    // Dummy implementation for voice-to-text: replace with actual API call
    public String detectVoice(byte[] audio, String sessionId) {
        log.info("[{}] Voice detection requested.", sessionId);
        if (audio == null || audio.length == 0) {
            log.warn("[{}] Empty audio bytes for voice detection.", sessionId);
            auditLogService.log(AuditLogEntry.fallback(
                    "Voice detection called with empty input.", null));
            return "[No audio provided]";
        }
        try {
            // TODO: Integrate with real service (OpenAI Whisper, Google Speech-to-Text, etc)
            String transcribed = "Transcription completed: [SIMULATED]";
            log.info("[{}] Voice successfully transcribed.", sessionId);
            auditLogService.log(AuditLogEntry.synthesis(
                    "Voice detected and transcribed.", null));
            return transcribed;
        } catch (Exception ex) {
            log.error("[{}] Voice detection failed: {}", sessionId, ex.getMessage());
            auditLogService.log(AuditLogEntry.exception(
                    "Voice detection failed.", Map.of("error", ex.getMessage())));
            return "[Transcription error]";
        }
    }
}