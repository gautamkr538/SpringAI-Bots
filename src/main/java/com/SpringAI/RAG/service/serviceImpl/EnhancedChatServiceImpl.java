package com.SpringAI.RAG.service.serviceImpl;

import com.SpringAI.RAG.dto.*;
import com.SpringAI.RAG.exception.ChatServiceException;
import com.SpringAI.RAG.helper.*;
import com.SpringAI.RAG.service.EnhancedChatService;
import com.SpringAI.RAG.utils.ModerationService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class EnhancedChatServiceImpl implements EnhancedChatService {

    private static final Logger log = LoggerFactory.getLogger(EnhancedChatServiceImpl.class);

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final ModerationService moderationService;
    private final PromptTemplateHelper promptHelper;
    private final CitationHelper citationHelper;
    private final QuestionGenerationHelper questionHelper;
    private final SourceAttributionHelper attributionHelper;
    private final QueryContextExtractor contextExtractor;
    private final SearchStrategyHelper searchStrategy;

    public EnhancedChatServiceImpl(
            ChatClient.Builder chatClientBuilder,
            @Qualifier("customVectorStore") VectorStore vectorStore,
            ModerationService moderationService,
            PromptTemplateHelper promptHelper,
            CitationHelper citationHelper,
            QuestionGenerationHelper questionHelper,
            SourceAttributionHelper attributionHelper,
            QueryContextExtractor contextExtractor,
            SearchStrategyHelper searchStrategy) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.moderationService = moderationService;
        this.promptHelper = promptHelper;
        this.citationHelper = citationHelper;
        this.questionHelper = questionHelper;
        this.attributionHelper = attributionHelper;
        this.contextExtractor = contextExtractor;
        this.searchStrategy = searchStrategy;
    }

    @Override
    public EnhancedChatResponse chat(String question) {
        final String sessionId = UUID.randomUUID().toString();
        final long startTime = System.currentTimeMillis();

        log.info("[{}] Chat query: '{}'", sessionId, question);

        try {
            // Step 1: Content moderation
            moderationService.validate(question);

            // Step 2: Extract query context using LLM
            QueryContext queryContext = contextExtractor.extractContext(question);
            log.info("[{}] Query context: category={}, specificity={}, priority={}",
                    sessionId, queryContext.getCategory(),
                    queryContext.getSpecificity(), queryContext.getPriority());

            // Step 3: Create advanced search request based on context
            SearchRequest searchRequest = searchStrategy.createAdvancedSearchRequest(question, queryContext);
            log.debug("[{}] Search config: topK={}, threshold={}",
                    sessionId, searchRequest.getTopK(), searchRequest.getSimilarityThreshold());

            // Step 4: Execute vector search
            List<Document> documents = vectorStore.similaritySearch(searchRequest);

            // Step 5: Fallback strategy if no results
            if ((documents == null || documents.isEmpty()) && queryContext.hasCategory()) {
                log.warn("[{}] Primary search returned no results, trying fallback", sessionId);
                SearchRequest fallbackRequest = searchStrategy.createFallbackSearchRequest(question);
                documents = vectorStore.similaritySearch(fallbackRequest);
            }

            boolean hasDocuments = (documents != null && !documents.isEmpty());

            if (!hasDocuments) {
                log.warn("[{}] No relevant documents found after fallback", sessionId);
                return buildGeneralKnowledgeResponse(question, sessionId, queryContext);
            }

            // Step 6: Extract citations and build context
            List<Citation> citations = citationHelper.extractCitations(documents);
            double confidence = citationHelper.calculateConfidence(citations);
            String docContext = citationHelper.buildContext(documents);

            log.info("[{}] Retrieved {} documents with avg confidence {:.2f}",
                    sessionId, documents.size(), confidence);

            // Step 7: Generate answer
            String answer = generateAnswer(question, docContext, queryContext);

            // Step 8: Determine source attribution
            String attribution = attributionHelper.determineAttribution(answer);

            // Step 9: Generate related questions
            List<String> relatedQuestions = questionHelper.generateRelated(question, answer);

            final long duration = System.currentTimeMillis() - startTime;
            log.info("[{}] Response generated in {}ms | Confidence: {:.2f} | Source: {} | Citations: {}",
                    sessionId, duration, confidence, attribution, citations.size());

            return EnhancedChatResponse.builder()
                    .answer(answer)
                    .citations(citations)
                    .confidenceScore(confidence)
                    .sourceAttribution(attribution)
                    .suggestedQuestions(relatedQuestions)
                    .followUpPrompt(promptHelper.buildFollowUpPrompt())
                    .timestamp(LocalDateTime.now())
                    .sessionId(sessionId)
                    .build();

        } catch (Exception e) {
            log.error("[{}] Chat error", sessionId, e);
            throw new ChatServiceException("Chat failed: " + e.getMessage(), e);
        }
    }

    private String generateAnswer(String question, String docContext, QueryContext queryContext) {
        // Enhanced prompt with query context awareness
        var prompt = promptHelper.createContextAwarePrompt(question, docContext, queryContext);
        return chatClient.prompt(prompt).call().content();
    }

    private EnhancedChatResponse buildGeneralKnowledgeResponse(String question, String sessionId, QueryContext queryContext) {

        log.info("[{}] Building general knowledge response", sessionId);

        var prompt = promptHelper.createGeneralKnowledgePrompt(question);
        String answer = chatClient.prompt(prompt).call().content();

        List<String> faqSuggestions = questionHelper.generateFAQSuggestions(vectorStore);

        String finalAnswer = answer + "\n\n---\n📋 Questions I can answer from documents:\n" +
                faqSuggestions.stream()
                        .map(q -> "• " + q)
                        .reduce("", (a, b) -> a + "\n" + b);

        return EnhancedChatResponse.builder()
                .answer(finalAnswer)
                .citations(Collections.emptyList())
                .confidenceScore(0.0)
                .sourceAttribution("general-knowledge")
                .suggestedQuestions(faqSuggestions)
                .followUpPrompt(promptHelper.buildFollowUpPrompt())
                .timestamp(LocalDateTime.now())
                .sessionId(sessionId)
                .build();
    }
}