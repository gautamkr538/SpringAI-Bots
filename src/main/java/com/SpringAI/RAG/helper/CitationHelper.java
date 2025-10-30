package com.SpringAI.RAG.helper;

import com.SpringAI.RAG.dto.Citation;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Component
public class CitationHelper {
    private static final Logger log = LoggerFactory.getLogger(CitationHelper.class);

    public List<Citation> extractCitations(List<Document> documents) {
        List<Citation> citations = new ArrayList<>();
        if (documents == null || documents.isEmpty()) {
            log.warn("extractCitations called with null or empty documents list.");
            return citations;
        }
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            if (doc == null) {
                log.warn("Null document at index {}", i);
                continue;
            }
            Map<String, Object> metadata = doc.getMetadata() != null ? doc.getMetadata() : Collections.emptyMap();

            double score = 0.75;
            try {
                if (metadata.containsKey("distance") && metadata.get("distance") instanceof Double) {
                    score = Math.max(0.0, 1.0 - (Double) metadata.get("distance"));
                } else if (doc.getScore() != null) {
                    score = doc.getScore();
                }
            } catch (Exception ex) {
                log.warn("Score calculation failed for document {}: {}", doc.getId(), ex.getMessage());
            }

            Integer page = null;
            try {
                Object pageObj = metadata.get("page_number");
                if (pageObj instanceof Integer) {
                    page = (Integer) pageObj;
                } else if (pageObj != null) {
                    page = Integer.valueOf(pageObj.toString());
                }
            } catch (Exception ex) {
                log.warn("Page number extraction failed for document {}: {}", doc.getId(), ex.getMessage());
            }

            String preview = "[No text]";
            try {
                String text = doc.getText();
                preview = (text != null && text.length() > 0)
                        ? text.substring(0, Math.min(300, text.length()))
                        : "[No text]";
            } catch (Exception ex) {
                log.warn("Text preview failed for document {}: {}", doc.getId(), ex.getMessage());
            }

            citations.add(Citation.builder()
                    .passageText(preview + "...")
                    .documentSource("Passage #" + (i + 1))
                    .pageNumber(page)
                    .relevanceScore(score)
                    .metadata(metadata)
                    .build());
        }
        log.info("extractCitations returned {} citation(s).", citations.size());
        return citations;
    }

    public double calculateConfidence(List<Citation> citations) {
        if (citations == null || citations.isEmpty()) return 0.0;
        double avg = citations.stream()
                .filter(Objects::nonNull)
                .mapToDouble(c -> c.getRelevanceScore() != null ? c.getRelevanceScore() : 0.0)
                .average()
                .orElse(0.0);
        log.info("Calculated average confidence: {}", avg);
        return avg;
    }

    public String buildContext(List<Document> documents) {
        StringBuilder context = new StringBuilder();
        if (documents == null || documents.isEmpty()) return "[No context, no documents found]";
        for (int i = 0; i < documents.size(); i++) {
            try {
                Document doc = documents.get(i);
                context.append("--- PASSAGE ").append(i + 1).append(" ---\n");
                String text = (doc != null) ? doc.getText() : "[No text]";
                context.append(text != null ? text : "[No text]").append("\n\n");
            } catch (Exception ex) {
                log.warn("Error building context for passage {}: {}", i + 1, ex.getMessage());
            }
        }
        log.info("Built document context for {} passage(s).", documents.size());
        return context.toString();
    }
}