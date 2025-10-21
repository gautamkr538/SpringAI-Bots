package com.SpringAI.RAG.service.serviceImpl;

import com.SpringAI.RAG.config.ModerationThresholds;
import com.SpringAI.RAG.dto.BlogPostResponseDTO;
import com.SpringAI.RAG.exception.ChatServiceException;
import com.SpringAI.RAG.service.ChatService;
import com.SpringAI.RAG.utils.ModerationService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.moderation.*;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.openai.audio.speech.SpeechPrompt;
import org.springframework.ai.openai.audio.speech.SpeechResponse;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ChatServiceImpl implements ChatService {

    private final ChatClient chatClient;
    private final OpenAiImageModel imageModel;
    private final OpenAiAudioSpeechModel speechModel;
    private final ModerationService moderationService;

    @Autowired
    @Qualifier("customVectorStore")
    private VectorStore vectorStore;

    private final JdbcTemplate jdbcTemplate;

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    public ChatServiceImpl(ChatClient.Builder chatClientBuilder, OpenAiImageModel imageModel, OpenAiAudioSpeechModel speechModel, ModerationService moderationService, VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.chatClient = chatClientBuilder.build();
        this.imageModel = imageModel;
        this.speechModel = speechModel;
        this.moderationService = moderationService;
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void initializeVectorStore(MultipartFile file) {
        try {
            log.info("Starting vector store initialization");
            jdbcTemplate.update("delete from vector_store");
            Resource resource = new InputStreamResource(file.getInputStream());
            // Configure PDF reader to process the file
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                    .withPageExtractedTextFormatter(new ExtractedTextFormatter.Builder()
                            .withNumberOfBottomTextLinesToDelete(3)
                            .withNumberOfTopTextLinesToDelete(3)
                            .withNumberOfTopPagesToSkipBeforeDelete(0)
                            .build())
                    .withPagesPerDocument(1)
                    .build();
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource, config);
            List<Document> textContent = pdfReader.get();
            // Detect URLs in the extracted text
            List<Document> enhancedContent = textContent.stream()
                    .map(document -> {
                        String content = document.getText();
                        List<String> urls = extractUrls(content);
                        // Append URLs to the content
                        if (!urls.isEmpty()) {
                            content += "\nExtracted URLs:\n" + String.join("\n", urls);
                        }
                        assert content != null;
                        return new Document(content);
                    }).toList();
            // Split extracted text into tokens and store in vector_store
            TokenTextSplitter textSplitter = new TokenTextSplitter();
            vectorStore.accept(textSplitter.apply(enhancedContent));
            log.info("Vector store initialized successfully");
        } catch (Exception e) {
            log.error("Unexpected error during vector store initialization", e);
            throw new ChatServiceException("Unexpected error during vector store initialization", e);
        }
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
    public ResponseEntity<String> chatBotForVectorStore(String question) {
        log.info("Received query to ChatBot: {}", question);
        try {

            // Check for content violations with custom thresholds
            moderationService.validate(question);

            List<Document> similarDocuments = this.vectorStore.similaritySearch(question);
            assert similarDocuments != null;
            String documents = similarDocuments.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining(System.lineSeparator()));
            // Prepare prompt for code generation
            String template = """
                You are an expert document analyst specializing in accurate information retrieval and contextual analysis.
                
                PRIMARY ROLE:
                Provide precise, comprehensive answers based on the DOCUMENTS below while maintaining complete transparency about information sources.
                
                RESPONSE GUIDELINES:
                
                1. DOCUMENT-AVAILABLE INFORMATION:
                   - Answer confidently using document content
                   - Synthesize information from multiple sections when relevant
                   - Provide comprehensive details without citing "according to the document"
                   - Maintain original context and meaning
                
                2. DOCUMENT-UNAVAILABLE INFORMATION:
                   - State clearly: "This information is not in the provided documents."
                   - Optionally add: "Based on general knowledge: [your answer]"
                   - Distinguish between document facts and external knowledge
                
                3. PARTIAL INFORMATION:
                   - Prioritize document data first
                   - Supplement with general knowledge only when necessary
                   - Format: "The documents show [fact]. Additionally, [supplementary info]."
                
                4. RESPONSE QUALITY:
                   - Use clear, professional language
                   - Structure complex answers with bullet points or numbered lists
                   - Include relevant examples and context
                   - Ensure accuracy without speculation
                
                5. SPECIAL HANDLING:
                   - Ambiguous queries: Request clarification while providing available information
                   - Conflicting information: Present both perspectives and note discrepancies
                   - Sensitive topics: Maintain objectivity and factual presentation
                
                DOCUMENTS:
                {documents}
                """;

            SystemMessage systemMessage = new SystemMessage(template.replace("{documents}", documents));
            UserMessage userMessage = new UserMessage(question);
            Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
            log.info("Prompt sent");
            var response = chatClient.prompt(prompt).call();
            var result = response.content();
            if (result == null) {
                throw new ChatServiceException("OpenAI returned null or empty content");
            }
            log.info("OpenAI returned: {}", result);
            return ResponseEntity.ok().body(result);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("OpenAI HTTP error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ChatServiceException("OpenAI API error: " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            log.error("OpenAI call failed: {}", e.getMessage(), e);
            throw new ChatServiceException("OpenAI API call failed. Try again later.", e);
        } catch (Exception e) {
            log.error("Unexpected error during chatbot response generation", e);
            throw new ChatServiceException("Unexpected error during chatbot response generation", e);
        }
    }

    @Override
    public BlogPostResponseDTO blogPostBot(String question) {
        log.info("Received query for BlogBot: {}", question);
        try {

            // Check for content violations with custom thresholds
            moderationService.validate(question);

            List<Document> similarDocuments = this.vectorStore.similaritySearch(question);
            assert similarDocuments != null;
            String documents = similarDocuments.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining(System.lineSeparator()));
            // Prepare prompt for Blog generation
            String template = """
                        You are an expert technical blog writer and educator, skilled in transforming raw material into engaging, high-quality posts.
                    
                        PRIMARY ROLE:
                        Create blog posts based exclusively on the DOCUMENTS and TOPIC provided, ensuring clarity, engagement, and authoritative value.
                    
                        RESPONSE GUIDELINES:
                    
                        1. INFORMATION SOURCES:
                           - Rely solely on the DOCUMENTS for technical details
                           - Where information is missing, begin with: "This topic is not fully covered in the provided documents. Based on general knowledge:"
                           - Clearly distinguish document-based points from external info
                    
                        2. BLOG STRUCTURE:
                           - Introduction: Grab attention, establish relevance, preview takeaways (75-100 words)
                           - Body: Three clear, well-defined sections with subheadings, amounting to 300-350 words in total
                           - Each section: Explain with examples, analogies, and facts
                           - Conclusion: Synthesize main points and add a call-to-action (75-100 words)
                    
                        3. STYLE AND CLARITY:
                           - Use conversational, professional language
                           - Break up text with headings and short paragraphs
                           - Explain technical terms simply for a broader audience
                           - Avoid jargon unless explained
                    
                        4. RESPONSE QUALITY:
                           - Integrate facts and numbers for credibility
                           - Use bullet points or numbered lists for complex info
                           - Ensure the writing is informative, actionable, and engaging
                    
                        5. SPECIAL HANDLING:
                           - For ambiguous topics: Request clarification and provide your best response
                           - If document info conflicts with general knowledge, note the discrepancy
                    
                        DOCUMENTS:
                        {documents}
                    
                        TOPIC:
                        {topic}
                        """;

            SystemMessage systemMessage = new SystemMessage(template.replace("{documents}", documents));
            UserMessage userMessage = new UserMessage(question);
            Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
            log.info("Prompt sent");
            var response = chatClient.prompt(prompt).call();
            var result = response.entity(BlogPostResponseDTO.class);
            if (result == null) {
                throw new ChatServiceException("OpenAI returned null or empty content");
            }
            log.info("OpenAI returned: {}", result);
            return result;
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("OpenAI HTTP error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ChatServiceException("OpenAI API error: " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            log.error("OpenAI call failed: {}", e.getMessage(), e);
            throw new ChatServiceException("OpenAI API call failed. Try again later.", e);
        } catch (Exception e) {
            log.error("Unexpected error during chatbot response generation", e);
            throw new ChatServiceException("Unexpected error during chatbot response generation", e);
        }
    }

    @Override
    public ResponseEntity<String> ImageDetectionBot(MultipartFile image, String question) {
        log.info("Received query for imageDetection");
        try {
            String template = """
                You are a computer vision expert specializing in detailed image analysis.
            
                PRIMARY ROLE:
                Perform thorough analysis and interpretation of the given image, answering the USER QUESTION if one is provided.
            
                RESPONSE GUIDELINES:
            
                1. OBJECTIVE OBSERVATIONS:
                   - Describe the overall composition, key elements, and context
                   - Identify objects, people, text, and activities
                   - Assess environmental cues (location, time of day, weather, etc.)
            
                2. QUESTION-FOCUSED ANALYSIS:
                   - If a USER QUESTION is provided, tailor your analysis directly to address it
                   - If unclear or not answerable, state your uncertainty clearly
            
                3. CONFIDENCE LEVELS:
                   - Indicate your confidence as High, Medium, Low, or Unable to determine for relevant points
                   - Avoid speculation and clearly separate observations from interpretations
            
                4. RESPONSE QUALITY:
                   - Use clear, professional language
                   - Present complex findings in bullet points if needed
                   - Focus on actionable, reliable detail
            
                5. SPECIAL HANDLING:
                   - For ambiguous images or missing details, state limitations
                   - For technical/scientific images, explain the content as far as possible
            
                USER QUESTION:
                {question}
                """;

            String formattedTemplate = "";
            if(question != null) {
                formattedTemplate = template.replace("{question}", question);
            }
            var response = chatClient.prompt()
                    .system(formattedTemplate)
                    .user(userSpec -> {
                                assert question != null;
                                userSpec
                                        .text(question)
                                        .media(MimeTypeUtils.IMAGE_JPEG, image.getResource());
                            }
                    )
                    .call();
            String result = response.content();
            if (result == null || result.isEmpty()) {
                throw new ChatServiceException("OpenAI returned null or empty content");
            }
            log.info("Image analysis completed");
            log.info("OpenAI returned: {}", result);
            return ResponseEntity.ok().body(result);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("OpenAI HTTP error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ChatServiceException("OpenAI API error: " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            log.error("OpenAI call failed: {}", e.getMessage(), e);
            throw new ChatServiceException("OpenAI API call failed. Try again later.", e);
        } catch (Exception e) {
            log.error("Unexpected error during image detection", e);
            throw new ChatServiceException("Unexpected error during image detection", e);
        }
    }

    @Override
    public ResponseEntity<String> ImageGenerationBot(String prompt) {
        log.info("Received query for imageGeneration");
        try {

            // Check for content violations with custom thresholds
            moderationService.validate(prompt);

            String template = """
                    You are an Image Generation Briefing Specialist skilled at producing precise, creative prompts for DALL-E 3 based on user requests.
                
                    PRIMARY ROLE:
                    Convert the USER REQUEST below into a detailed, artistically rich prompt that can be used to generate a high-quality image.
                
                    RESPONSE GUIDELINES:
                
                    1. PROMPT STRUCTURE:
                       - Clearly identify the main subject, visual style, setting, mood, lighting, colors, and any specific requirements
                       - Use descriptive adjectives and creative detail
                       - If non-image requests are received, respond: "This bot generates images only. Please use ChatBot for non-image queries."
                
                    2. EXAMPLES AND MODIFIERS:
                       - Include style modifiers (e.g., 'photorealistic', 'minimalist', 'futuristic')
                       - Suggest camera perspective or composition when relevant
                
                    3. OUTPUT FORMAT:
                       - Output a single, flowing text prompt suitable for direct input to DALL-E 3
                       - Do not use bullet points or lists in the output prompt
                
                    4. RESPONSE QUALITY:
                       - Be specific, imaginative, and concise
                       - Avoid ambiguity or mutually conflicting instructions
                       - Focus on visual and stylistic clarity
                
                    USER REQUEST:
                    {userPrompt}
                    """;

            String formattedTemplate = template.replace("{prompt}", prompt);

            ImageOptions options = OpenAiImageOptions.builder()
                    .model("dall-e-3")
                    .build();

            ImagePrompt imagePrompt = new ImagePrompt(formattedTemplate, options);
            ImageResponse imageResponse = imageModel.call(imagePrompt);

            String imageUrl = imageResponse.getResult().getOutput().getUrl();
            if (imageUrl == null || imageUrl.isEmpty()) {
                throw new ChatServiceException("Image generation service returned no image url");
            }
            log.info("Image generation completed - URL: {}", imageUrl);
            return ResponseEntity.ok().body(imageUrl);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("Image generation HTTP error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ChatServiceException("Image generation API error: " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            log.error("Image generation call failed: {}", e.getMessage(), e);
            throw new ChatServiceException("Image generation API call failed. Try again later.", e);
        } catch (Exception e) {
            log.error("Unexpected error during image generation", e);
            throw new ChatServiceException("Unexpected error during image generation", e);
        }
    }

    @Override
    public ResponseEntity<byte[]> VoiceGenerationBot(String text) {
        log.info("Received query for voiceGeneration");
        try {
            // Check for content violations with custom thresholds
            moderationService.validate(text);

            String template = """
                You are an expert document analyst specializing in accurate information retrieval and contextual analysis.
                
                PRIMARY ROLE:
                Provide precise, comprehensive answers based on the DOCUMENTS below while maintaining complete transparency about information sources.
                
                RESPONSE GUIDELINES:
                
                1. DOCUMENT-AVAILABLE INFORMATION:
                   - Answer confidently using document content
                   - Synthesize information from multiple sections when relevant
                   - Provide comprehensive details without citing "according to the document"
                   - Maintain original context and meaning
                
                2. DOCUMENT-UNAVAILABLE INFORMATION:
                   - State clearly: "This information is not in the provided documents."
                   - Optionally add: "Based on general knowledge: [your answer]"
                   - Distinguish between document facts and external knowledge
                
                3. PARTIAL INFORMATION:
                   - Prioritize document data first
                   - Supplement with general knowledge only when necessary
                   - Format: "The documents show [fact]. Additionally, [supplementary info]."
                
                4. RESPONSE QUALITY:
                   - Use clear, professional language
                   - Structure complex answers with bullet points or numbered lists
                   - Include relevant examples and context
                   - Ensure accuracy without speculation
                
                5. SPECIAL HANDLING:
                   - Ambiguous queries: Request clarification while providing available information
                   - Conflicting information: Present both perspectives and note discrepancies
                   - Sensitive topics: Maintain objectivity and factual presentation
                
                DOCUMENTS:
                {prompt}
                """;

            var voiceScript ="";
            if(text != null && !text.isEmpty()) {
                SystemMessage systemMessage = new SystemMessage(template.replace("{prompt}", text));
                UserMessage userMessage = new UserMessage(text);
                Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
                log.info("Prompt sent");
                var response = chatClient.prompt(prompt).call();
                voiceScript = response.content();
            }

            if (voiceScript == null) {
                throw new ChatServiceException("OpenAI returned null or empty content");
            }
            log.info("Generated voice script: {}", voiceScript);
            OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
                    .model("tts-1-hd")
                    .voice(OpenAiAudioApi.SpeechRequest.Voice.ALLOY)
                    .responseFormat(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3)
                    .speed(1.0f)
                    .build();

            SpeechPrompt speechPrompt = new SpeechPrompt(voiceScript, options);
            SpeechResponse speechResponse = speechModel.call(speechPrompt);

            byte[] audioBytes = speechResponse.getResult().getOutput();

            if (audioBytes == null || audioBytes.length == 0) {
                throw new ChatServiceException("Voice generation service returned no audio data");
            }
            log.info("Voice generation completed - Size: {} bytes", audioBytes.length);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Response.mp3\"")
                    .body(audioBytes);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("Voice generation HTTP error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ChatServiceException("Voice generation API error: " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            log.error("Voice generation call failed: {}", e.getMessage(), e);
            throw new ChatServiceException("Voice generation API call failed. Try again later.", e);
        } catch (Exception e) {
            log.error("Unexpected error during voice generation", e);
            throw new ChatServiceException("Unexpected error during voice generation", e);
        }
    }

    @Override
    public String codeGeneratorBot(String prompt) {
        log.info("Received code generation prompt: {}", prompt);
        try {

            // Check for content violations with custom thresholds
            moderationService.validate(prompt);

            // Prepare prompt for code generation
            String template = """
                    You are a production-grade Code Generator and Software Architect.
                
                    PRIMARY ROLE:
                    Output correct, efficient, and maintainable code according to the USER REQUEST below, following best practices for language and framework.
                
                    RESPONSE GUIDELINES:
                
                    1. CODE CONTENT:
                       - Generate complete code, including all imports, class definitions, functions, comments, configuration, and usage where needed
                       - Default to Java with Spring Boot and Maven unless another language or framework is requested
                
                    2. CODE QUALITY:
                       - Use consistent naming conventions and formatting
                       - Include meaningful inline comments for complex logic
                       - Implement error handling and validation
                       - Cover edge cases
                
                    3. RESPONSE STRUCTURE:
                       - Output only code (no explanations or additional text unless explicitly requested)
                       - For non-code requests, respond: "This is the Code Generator Bot. Please use ChatBot for information and explanations."
                       - If the request is ambiguous, ask for clarification before providing output
                
                    4. SPECIAL HANDLING:
                       - For requests specifying particular versions or frameworks, comply exactly
                       - If the task cannot be solved, output a clear, helpful error message
                
                    USER REQUEST:
                    {userRequest}
                    """;

            String formattedPrompt = template.replace("{prompt}", prompt);
            SystemMessage systemMessage = new SystemMessage(formattedPrompt);
            UserMessage userMessage = new UserMessage(prompt);
            Prompt codePrompt = new Prompt(List.of(systemMessage, userMessage));
            log.info("Sending code generation prompt to ChatClient...");
            String generatedCode = chatClient.prompt(codePrompt).call().content();
            if (generatedCode == null || generatedCode.trim().isEmpty()) {
                throw new ChatServiceException("No response received from the Code Generator bot.");
            }
            log.info("Generated code: {}", generatedCode);
            return generatedCode;
        } catch (Exception e) {
            log.error("Error during code generation", e);
            throw new ChatServiceException("Unexpected error during code generation", e);
        }
    }
}