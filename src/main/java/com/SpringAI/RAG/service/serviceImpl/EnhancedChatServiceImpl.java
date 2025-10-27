package com.SpringAI.RAG.service.serviceImpl;

import com.SpringAI.RAG.dto.Citation;
import com.SpringAI.RAG.dto.EnhancedChatResponse;
import com.SpringAI.RAG.dto.QueryContext;
import com.SpringAI.RAG.exception.ChatServiceException;
import com.SpringAI.RAG.helper.CitationHelper;
import com.SpringAI.RAG.helper.PromptTemplateHelper;
import com.SpringAI.RAG.helper.QueryContextExtractor;
import com.SpringAI.RAG.helper.QuestionGenerationHelper;
import com.SpringAI.RAG.helper.SearchStrategyHelper;
import com.SpringAI.RAG.helper.SourceAttributionHelper;
import com.SpringAI.RAG.service.EnhancedChatService;
import com.SpringAI.RAG.utils.ModerationService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.ai.content.Media;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private final JdbcTemplate jdbcTemplate;

    public EnhancedChatServiceImpl(
            ChatClient.Builder chatClientBuilder,
            @Qualifier("customVectorStore") VectorStore vectorStore,
            ModerationService moderationService,
            PromptTemplateHelper promptHelper,
            CitationHelper citationHelper,
            QuestionGenerationHelper questionHelper,
            SourceAttributionHelper attributionHelper,
            QueryContextExtractor contextExtractor,
            SearchStrategyHelper searchStrategy, JdbcTemplate jdbcTemplate) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.moderationService = moderationService;
        this.promptHelper = promptHelper;
        this.citationHelper = citationHelper;
        this.questionHelper = questionHelper;
        this.attributionHelper = attributionHelper;
        this.contextExtractor = contextExtractor;
        this.searchStrategy = searchStrategy;
        this.jdbcTemplate = jdbcTemplate;
    }

    // Non-destructive ingestion, tracking fileBatchId in all chunks for traceability
    public void initializeVectorStore(MultipartFile file) {
        UUID batchId = UUID.randomUUID(); // Unique per upload batch
        try {
            log.info("Initializing vector store, batchId={}", batchId);

            if (file == null || file.isEmpty()) {
                log.error("No file provided for vector store initialization, batchId={}", batchId);
                throw new ChatServiceException("No file provided for vector store initialization");
            }
            String fileName = file.getOriginalFilename();
            String mimeTypeStr = file.getContentType();
            MimeType mimeType;
            try {
                mimeType = (mimeTypeStr != null) ? MimeType.valueOf(mimeTypeStr) : MimeType.valueOf("application/octet-stream");
            } catch (Exception ex) {
                log.warn("Invalid MIME type '{}', using default for batchId={}", mimeTypeStr, batchId);
                mimeType = MimeType.valueOf("application/octet-stream");
            }
            Resource resource = new InputStreamResource(file.getInputStream());

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("filename", fileName != null ? fileName : "Unknown");
            metadata.put("uploadTimestamp", System.currentTimeMillis());
            metadata.put("mimeType", mimeType.toString());
            metadata.put("filesize", file.getSize());
            metadata.put("fileBatchId", batchId.toString());

            List<Document> textContent = new ArrayList<>();
            if (mimeType.equals(Media.Format.DOC_PDF)) {
                PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                        .withPageExtractedTextFormatter(new ExtractedTextFormatter.Builder()
                                .withNumberOfBottomTextLinesToDelete(3)
                                .withNumberOfTopTextLinesToDelete(3)
                                .withNumberOfTopPagesToSkipBeforeDelete(0)
                                .build())
                        .withPagesPerDocument(1)
                        .build();
                PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource, config);
                textContent = pdfReader.get();
            } else if (mimeType.equals(Media.Format.DOC_TXT) ||
                    mimeType.equals(Media.Format.DOC_MD) ||
                    mimeType.equals(Media.Format.DOC_HTML)) {
                String content = new String(file.getBytes());
                metadata.put("detectedUrls", extractUrls(content));
                textContent = List.of(Document.builder().text(content).metadata(metadata).build());
            } else {
                Media media = Media.builder().mimeType(mimeType).data(resource).name(fileName).build();
                textContent = List.of(Document.builder().media(media).metadata(metadata).build());
            }

            List<Document> enhancedDocs = new ArrayList<>();
            for (Document doc : textContent) {
                Map<String, Object> docMeta = new HashMap<>(doc.getMetadata() != null ? doc.getMetadata() : metadata);
                docMeta.put("fileBatchId", batchId.toString());
                String txt = doc.getText();
                if (txt != null && !txt.isEmpty()) {
                    List<String> urls = extractUrls(txt);
                    if (!urls.isEmpty()) {
                        docMeta.put("detectedUrls", urls);
                    }
                }
                enhancedDocs.add(Document.builder()
                        .text(txt)
                        .media(doc.getMedia())
                        .metadata(docMeta)
                        .build());
            }

            TokenTextSplitter textSplitter = new TokenTextSplitter();
            List<Document> splits = textSplitter.apply(enhancedDocs);
            vectorStore.accept(splits);

            log.info("Vector store updated for file '{}' with batchId={}", fileName, batchId);
        } catch (Exception e) {
            log.error("Vector store init failed, file: '{}', error: {}", file != null ? file.getOriginalFilename() : "null", e.getMessage(), e);
            throw new ChatServiceException("Vector store initialization failed", e);
        }
    }

    // Use for multi-file batches: always pass same batchId for group consistency
    public List<Document> buildDocumentsFromFiles(List<MultipartFile> files, UUID batchId) {
        List<Document> docs = new ArrayList<>();
        if (files == null || files.isEmpty()) {
            log.warn("No files provided for document building, batchId={}", batchId);
            return docs;
        }

        for (MultipartFile file : files) {
            if (file == null) {
                log.warn("Encountered null file in upload list; skipping, batchId={}", batchId);
                continue;
            }
            try {
                String fileName = file.getOriginalFilename();
                String mimeTypeStr = file.getContentType();
                MimeType mimeType;
                try {
                    mimeType = (mimeTypeStr != null) ? MimeType.valueOf(mimeTypeStr) : MimeType.valueOf("application/octet-stream");
                } catch (Exception ex) {
                    log.warn("Invalid detected mimeType '{}' for file '{}'; using default, batchId={}", mimeTypeStr, fileName, batchId);
                    mimeType = MimeType.valueOf("application/octet-stream");
                }

                Resource resource = new InputStreamResource(file.getInputStream());

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("filename", fileName != null ? fileName : "unknown");
                metadata.put("uploadTimestamp", System.currentTimeMillis());
                metadata.put("mimeType", mimeType.toString());
                metadata.put("filesize", file.getSize());
                metadata.put("fileBatchId", batchId.toString());

                Document doc;
                if (mimeType.equals(Media.Format.DOC_TXT) || mimeType.equals(Media.Format.DOC_MD) || mimeType.equals(Media.Format.DOC_HTML)) {
                    String textContent = null;
                    try {
                        textContent = new String(file.getBytes());
                    } catch (Exception ex) {
                        log.warn("Failed to read text from '{}', batchId={}, error={}", fileName, batchId, ex.getMessage());
                    }
                    if (textContent != null) {
                        metadata.put("detectedUrls", extractUrls(textContent));
                        doc = Document.builder().text(textContent).metadata(metadata).build();
                    } else {
                        doc = Document.builder().media(Media.builder().mimeType(mimeType).data(resource).name(fileName).build()).metadata(metadata).build();
                    }
                } else {
                    doc = Document.builder().media(
                                    Media.builder().mimeType(mimeType).data(resource).name(fileName).build())
                            .metadata(metadata)
                            .build();
                }

                docs.add(doc);
                log.info("Added document: {} ({} bytes, mime={}, batchId={})", fileName, file.getSize(), mimeType, batchId);

            } catch (Exception e) {
                log.error("Failed to process file '{}', batchId={}, error={}", file != null ? file.getOriginalFilename() : null, batchId, e.getMessage(), e);
            }
        }
        return docs;
    }

    private List<String> extractUrls(String text) {
        List<String> urls = new ArrayList<>();
        String urlRegex = "(https?://[\\w\\-._~:/?\\[\\]@!$&'()*+,;=%]+)";
        Matcher matcher = Pattern.compile(urlRegex).matcher(text);
        while (matcher.find()) {
            urls.add(matcher.group());
        }
        return urls;
    }

    @Override
    public EnhancedChatResponse chat(String question) {
        final String sessionId = UUID.randomUUID().toString();
        final long startTime = System.currentTimeMillis();

        log.info("[{}] Chat query: '{}'", sessionId, question);

        try {
            moderationService.validate(question);

            QueryContext queryContext = contextExtractor.extractContext(question);
            log.info("[{}] Query context: category={}, specificity={}, priority={}", sessionId, queryContext.getCategory(), queryContext.getSpecificity(), queryContext.getPriority());

            SearchRequest searchRequest = searchStrategy.createAdvancedSearchRequest(question, queryContext);
            log.debug("[{}] Search config: topK={}, threshold={}", sessionId, searchRequest.getTopK(), searchRequest.getSimilarityThreshold());

            List<Document> documents = vectorStore.similaritySearch(searchRequest);

            if ((documents == null || documents.isEmpty()) && queryContext.hasCategory()) {
                log.warn("[{}] Primary search returned no results, trying fallback", sessionId);
                SearchRequest fallbackRequest = searchStrategy.createFallbackSearchRequest(question);
                documents = vectorStore.similaritySearch(fallbackRequest);
            }

            boolean hasDocuments = (documents != null && !documents.isEmpty());

            if (!hasDocuments) {
                log.warn("[{}] No relevant documents found after fallback", sessionId);
                return buildGeneralKnowledgeResponse(question, sessionId);
            }

            List<Citation> citations = citationHelper.extractCitations(documents);
            double confidence = citationHelper.calculateConfidence(citations);
            String docContext = citationHelper.buildContext(documents);

            if (citations.isEmpty()) {
                log.warn("[{}] CitationHelper returned no citations; creating fallback citation from documents", sessionId);
                Citation fallback = createGeneratedCitation("Documents (generated)",
                        summarizeForCitation(docContext, 400),
                        0.5,
                        "No citations extracted from documents; created fallback from document text");
                citations = List.of(fallback);
                confidence = fallback.getRelevanceScore();
            }

            log.info("[{}] Retrieved {} documents with avg confidence {}", sessionId, documents.size(), String.format("%.2f", confidence));

            String answer = generateAnswer(question, docContext, queryContext);

            String attribution = attributionHelper.determineAttribution(answer);

            List<String> relatedQuestions = questionHelper.generateRelated(question, answer);

            final long duration = System.currentTimeMillis() - startTime;
            log.info("[{}] Response generated in {}ms | Confidence: {} | Source: {} | Citations: {}",
                    sessionId, duration, String.format("%.2f", confidence), attribution, citations.size());

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
        var prompt = promptHelper.createContextAwarePrompt(question, docContext, queryContext);
        return chatClient.prompt(prompt).call().content();
    }

    private EnhancedChatResponse buildGeneralKnowledgeResponse(String question, String sessionId) {
        log.info("[{}] Building general knowledge response", sessionId);

        var prompt = promptHelper.createGeneralKnowledgePrompt(question);
        String answer = chatClient.prompt(prompt).call().content();

        List<String> faqSuggestions = questionHelper.generateFAQSuggestions(vectorStore);

        String finalAnswer = answer + "\n\n---\n Questions I can answer from documents:\n" +
                faqSuggestions.stream()
                        .map(q -> "• " + q)
                        .reduce("", (a, b) -> a + "\n" + b);

        Citation generatedCitation = createGeneratedCitation("LLM - general knowledge",
                summarizeForCitation(answer, 400),
                0.3,
                "Generated citation for general knowledge answer when no documents are available");

        return EnhancedChatResponse.builder()
                .answer(finalAnswer)
                .citations(List.of(generatedCitation))
                .confidenceScore(0.0)
                .sourceAttribution("general-knowledge")
                .suggestedQuestions(faqSuggestions)
                .followUpPrompt(promptHelper.buildFollowUpPrompt())
                .timestamp(LocalDateTime.now())
                .sessionId(sessionId)
                .build();
    }

    private String summarizeForCitation(String text, int maxChars) {
        if (text == null || text.isEmpty()) return "[No text available]";
        String trimmed = text.trim();
        if (trimmed.length() <= maxChars) return trimmed;
        return trimmed.substring(0, maxChars) + "...";
    }

    private Citation createGeneratedCitation(String source, String textPreview, Double score, String reason) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("generatedBy", "LLM");
        metadata.put("reason", reason);
        metadata.put("generatedAt", LocalDateTime.now().toString());

        return Citation.builder()
                .passageText(textPreview != null ? textPreview : "[no excerpt]")
                .documentSource(source)
                .pageNumber(null)
                .relevanceScore(score)
                .metadata(metadata)
                .build();
    }
}