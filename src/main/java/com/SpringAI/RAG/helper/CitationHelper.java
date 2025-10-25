package com.SpringAI.RAG.helper;

import com.SpringAI.RAG.dto.Citation;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class CitationHelper {

    public List<Citation> extractCitations(List<Document> documents) {
        List<Citation> citations = new ArrayList<>();
        
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            Map<String, Object> metadata = doc.getMetadata();
            
            double score = metadata.containsKey("distance") 
                    ? 1.0 - (Double) metadata.get("distance") 
                    : 0.75;
            
            Integer page = (Integer) metadata.getOrDefault("page_number", null);
            assert doc.getText() != null;
            String preview = doc.getText().substring(0, Math.min(300, doc.getText().length()));
            
            citations.add(Citation.builder()
                    .passageText(preview + "...")
                    .documentSource("Passage #" + (i + 1))
                    .pageNumber(page)
                    .relevanceScore(score)
                    .metadata(metadata)
                    .build());
        }
        
        return citations;
    }

    public double calculateConfidence(List<Citation> citations) {
        return citations.stream()
                .mapToDouble(Citation::getRelevanceScore)
                .average()
                .orElse(0.0);
    }

    public String buildContext(List<Document> documents) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            context.append("--- PASSAGE ").append(i + 1).append(" ---\n");
            context.append(documents.get(i).getText()).append("\n\n");
        }
        return context.toString();
    }
}
