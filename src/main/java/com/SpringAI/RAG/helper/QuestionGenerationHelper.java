package com.SpringAI.RAG.helper;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class QuestionGenerationHelper {

    private static final Logger log = LoggerFactory.getLogger(QuestionGenerationHelper.class);
    private static final int MAX_RELATED_QUESTIONS = 3;
    private static final int FAQ_SAMPLE_SIZE = 10;

    private final ChatClient chatClient;

    public QuestionGenerationHelper(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public List<String> generateRelated(String question, String answer) {
        log.debug("Generating related questions for: '{}'", question);

        String template = """
                You are a conversational AI assistant generating follow-up questions.
                
                TASK: Generate {maxQuestions} natural follow-up questions based on the conversation below.
                
                REQUIREMENTS:
                - Questions should explore related aspects not fully covered
                - Make questions specific and actionable
                - Use natural, conversational language
                - Questions should logically extend the conversation
                
                OUTPUT FORMAT:
                Return ONLY the questions, one per line, without numbering or bullets.
                
                USER QUESTION:
                {question}
                
                ANSWER PROVIDED:
                {answerPreview}
                """;

        String answerPreview = answer.length() > 500
                ? answer.substring(0, 500) + "..."
                : answer;

        try {
            PromptTemplate pt = new PromptTemplate(template);
            var prompt = pt.createMessage(Map.of(
                    "question", question,
                    "answerPreview", answerPreview,
                    "maxQuestions", String.valueOf(MAX_RELATED_QUESTIONS)
            ));

            String response = chatClient.prompt(new Prompt(List.of(prompt))).call().content();

            List<String> questions = Arrays.stream(response.split("\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .filter(s -> !s.matches("^\\d+\\..*")) // Remove numbered items
                    .limit(MAX_RELATED_QUESTIONS)
                    .collect(Collectors.toList());

            log.debug("Generated {} related questions", questions.size());
            return questions;

        } catch (Exception e) {
            log.error("Failed to generate related questions", e);
            return Collections.emptyList();
        }
    }

    public List<String> generateFAQSuggestions(VectorStore vectorStore) {
        log.debug("Generating FAQ suggestions from document corpus");

        try {
            // Using SearchRequest.builder() - Correct approach
            SearchRequest searchRequest = SearchRequest.builder()
                    .query("overview summary main topics key information")
                    .topK(FAQ_SAMPLE_SIZE)
                    .similarityThreshold(0.5)
                    .build();

            List<Document> docs = vectorStore.similaritySearch(searchRequest);

            if (docs == null || docs.isEmpty()) {
                log.warn("No documents found for FAQ generation");
                return getDefaultFAQs();
            }

            String sample = docs.stream()
                    .map(Document::getText)
                    .limit(5)
                    .collect(Collectors.joining("\n\n"))
                    .substring(0, Math.min(2000, docs.stream()
                            .mapToInt(d -> d.getText().length())
                            .sum()));

            String template = """
                    You are an FAQ question generator for document-based knowledge systems.
                    
                    TASK: Generate 5 specific, answerable questions based on the DOCUMENT SAMPLES below.
                    
                    REQUIREMENTS:
                    - Questions must be answerable from the document content
                    - Make questions specific and actionable
                    - Vary question types (what, how, when, who, why, where)
                    - Focus on the most important and commonly needed information
                    
                    OUTPUT FORMAT:
                    Return ONLY the questions, one per line, without numbering or bullets.
                    
                    DOCUMENT SAMPLES:
                    {sample}
                    """;

            PromptTemplate pt = new PromptTemplate(template);
            var prompt = pt.createMessage(Map.of("sample", sample));

            String response = chatClient.prompt(new Prompt(List.of(prompt))).call().content();

            assert response != null;
            List<String> faqs = Arrays.stream(response.split("\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .filter(s -> !s.matches("^\\d+\\..*"))
                    .limit(5)
                    .collect(Collectors.toList());

            log.debug("Generated {} FAQ suggestions", faqs.size());
            return faqs.isEmpty() ? getDefaultFAQs() : faqs;

        } catch (Exception e) {
            log.error("Failed to generate FAQ suggestions", e);
            return getDefaultFAQs();
        }
    }

    private List<String> getDefaultFAQs() {
        return List.of(
                "What are the main topics covered in the documents?",
                "Can you provide a summary of the key information?",
                "What are the most important highlights?",
                "Are there any critical dates or deadlines mentioned?",
                "What actions or next steps are recommended?"
        );
    }
}