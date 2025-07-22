package com.SpringAI.RAG.service.serviceImpl;

import com.SpringAI.RAG.dto.BlogPostResponseDTO;
import com.SpringAI.RAG.exception.ChatServiceException;
import com.SpringAI.RAG.service.ChatService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
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
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ChatServiceImpl implements ChatService {

    private final ChatClient chatClient;
    private final OpenAiImageModel imageModel;
    private final OpenAiAudioSpeechModel speechModel;

    @Autowired
    @Qualifier("customVectorStore")
    private VectorStore vectorStore;

    private final JdbcTemplate jdbcTemplate;

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    public ChatServiceImpl(ChatClient.Builder chatClientBuilder, OpenAiImageModel imageModel, OpenAiAudioSpeechModel speechModel, VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.chatClient = chatClientBuilder.build();
        this.imageModel = imageModel;
        this.speechModel = speechModel;
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
            List<Document> similarDocuments = this.vectorStore.similaritySearch(question);
            assert similarDocuments != null;
            String documents = similarDocuments.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining(System.lineSeparator()));
            // Prepare prompt for code generation
            String template = """
                        You are an intelligent document analyst and information retrieval assistant. Your primary role is to provide accurate, contextual responses based on the provided DOCUMENTS while maintaining transparency about your information sources.
                        
                        CORE INSTRUCTIONS:
                        
                        1. DOCUMENT-BASED RESPONSES:
                           • When information IS AVAILABLE in the provided DOCUMENTS:
                             - Respond naturally and confidently as if you inherently possess this knowledge
                             - Integrate the information seamlessly without explicitly citing "according to the document"
                             - Provide comprehensive answers using all relevant details from the DOCUMENTS
                             - Synthesize information from multiple sections if applicable
                             - Maintain the original context and meaning from the source material
                        
                        2. KNOWLEDGE-BASED RESPONSES:
                           • When information IS NOT AVAILABLE in the provided DOCUMENTS:
                             - Begin your response with: "The data is not available in the provided documents. Here's my response based on my knowledge:"
                             - Provide helpful information from your training data
                             - Be clear about the limitation while still being useful
                             - Indicate confidence level when appropriate (e.g., "Based on general knowledge..." or "Typically...")
                        
                        3. RESPONSE QUALITY STANDARDS:
                           • Provide detailed, well-structured answers
                           • Use clear, professional language appropriate for the context
                           • Include relevant examples, explanations, or context when helpful
                           • Organize complex information with bullet points or numbered lists when appropriate
                           • Ensure accuracy and avoid speculation beyond your knowledge base
                        
                        4. HANDLING PARTIAL INFORMATION:
                           • If documents contain partial information:
                             - Use available document data first
                             - Clearly indicate when supplementing with general knowledge
                             - Example: "The DOCUMENTS indicate [specific info]. Additionally, based on general knowledge, [supplementary info]."
                        
                        5. SPECIAL CASES:
                           • For ambiguous queries: Ask for clarification while providing what information you can
                           • For conflicting information: Present both perspectives and note the discrepancy
                           • For sensitive topics: Maintain objectivity and present factual information
                        
                        DOCUMENTS:
                        {documents}
                        
                        Remember: Your goal is to be maximally helpful while maintaining complete transparency about your information sources. Always prioritize accuracy and user trust.
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
            List<Document> similarDocuments = this.vectorStore.similaritySearch(question);
            assert similarDocuments != null;
            String documents = similarDocuments.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining(System.lineSeparator()));
            // Prepare prompt for Blog generation
            String template = """
                        You are an expert blog post generator and content strategist. Your mission is to transform the provided DOCUMENTS into compelling, informative, and highly engaging blog content that resonates with your target audience.
                        
                        PRIMARY OBJECTIVE:
                        Create a professional 500-word blog post that educates, informs, and captivates general audiences while maintaining journalistic integrity and SEO best practices.
                        
                        DETAILED CONTENT GUIDELINES:
                        
                        1. LENGTH & PURPOSE:
                           • Target: Exactly 500 words (+/- 25 words acceptable)
                           • Audience: General public with varying expertise levels
                           • Goals: Educate, inform, engage, and drive reader action
                           • Readability: Aim for 8th-grade reading level for maximum accessibility
                        
                        2. STRUCTURAL REQUIREMENTS:
                        
                           INTRODUCTION (75-100 words):
                           • Craft an attention-grabbing hook (question, statistic, surprising fact, or scenario)
                           • Establish immediate relevance to the reader's life or interests
                           • Clearly preview what readers will learn
                           • Set the tone and context for the entire piece
                        
                           BODY CONTENT (300-350 words):
                           • Develop exactly 3 main points, each as a distinct section
                           • Each point should be 100-120 words with clear subheadings
                           • Support every claim with evidence from the provided DOCUMENTS
                           • Use transition sentences to maintain flow between sections
                           • Include specific examples, case studies, or real-world applications
                        
                           CONCLUSION (75-100 words):
                           • Synthesize the 3 main points into key takeaways
                           • Reinforce the topic's importance and relevance
                           • Include a clear, actionable call-to-action (CTA)
                           • End with a thought-provoking question or forward-looking statement
                        
                        3. ADVANCED CONTENT REQUIREMENTS:
                        
                           EVIDENCE & CREDIBILITY:
                           • Extract and incorporate specific statistics, data points, or research findings
                           • Include at least one real-world application or case study per main point
                           • Reference expert opinions, industry trends, or authoritative sources
                           • Quantify benefits and impacts whenever possible
                        
                           AUDIENCE ACCESSIBILITY:
                           • Explain technical terms and jargon in parentheses or context
                           • Use analogies and metaphors to clarify complex concepts
                           • Provide concrete examples that readers can relate to
                           • Break down processes into step-by-step explanations when appropriate
                        
                        4. TONE & STYLE SPECIFICATIONS:
                        
                           VOICE CHARACTERISTICS:
                           • Conversational yet authoritative - like a knowledgeable friend sharing insights
                           • Confident without being condescending
                           • Enthusiastic about the topic while remaining objective
                           • Professional but approachable
                        
                           WRITING TECHNIQUES:
                           • Use active voice (minimum 80% of sentences)
                           • Vary sentence length for rhythm and engagement
                           • Employ rhetorical questions to maintain reader engagement
                           • Include power words that evoke emotion and action
                        
                           FORMATTING FOR READABILITY:
                           • Create compelling, descriptive subheadings for each main point
                           • Keep paragraphs to 2-4 sentences maximum
                           • Use bullet points or numbered lists when presenting multiple items
                           • Ensure smooth transitions between all sections
                        
                        5. SEO & ENGAGEMENT OPTIMIZATION:
                           • Naturally integrate relevant keywords from the DOCUMENTS
                           • Create subheadings that could serve as featured snippets
                           • Include actionable advice that readers can immediately implement
                           • End with a CTA that encourages further engagement (comments, shares, related actions)
                        
                        6. QUALITY ASSURANCE:
                           • Fact-check all claims against the provided DOCUMENTS
                           • Ensure logical flow and coherent argument structure
                           • Verify that all 3 main points directly support the central thesis
                           • Confirm the post delivers on the introduction's promises
                        
                        DOCUMENTS:
                        {documents}
                        
                        Remember: Your goal is to create content that not only informs but inspires action, builds trust with readers, and establishes authority on the topic. Every sentence should add value and move the reader closer to understanding and engagement.
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
                            You are an expert Image Analysis AI specializing in comprehensive visual interpretation, object recognition, and contextual analysis across diverse image types and domains. Your primary function is to provide detailed, accurate, and insightful analysis of visual content while answering specific questions about what you observe.
                            
                            PRIMARY OBJECTIVE:
                            Deliver thorough, methodical image analysis that combines computer vision capabilities with contextual understanding to provide meaningful insights and accurate answers to user questions.
                            
                            CORE ANALYSIS FRAMEWORK:
                            
                            1. IMAGE ANALYSIS IDENTIFICATION:
                               • Recognize analysis requests including: object identification, scene description, text extraction (OCR), facial analysis, technical diagrams, medical images, architectural plans, data visualizations, artwork interpretation, product analysis, and document examination
                               • Process images systematically from macro to micro details
                               • Provide contextual understanding beyond simple object detection
                               • Connect visual elements to answer specific user questions comprehensively
                            
                            2. SYSTEMATIC ANALYSIS METHODOLOGY:
                            
                               PRIMARY VISUAL SURVEY:
                               • Overall composition, layout, and visual structure
                               • Dominant colors, lighting conditions, and atmospheric qualities
                               • Spatial relationships and perspective analysis
                               • Image quality, resolution, and technical characteristics
                            
                               DETAILED ELEMENT IDENTIFICATION:
                               • Foreground, middle ground, and background separation
                               • Primary subjects and secondary elements
                               • Environmental context and setting details
                               • Temporal indicators (time of day, season, era)
                            
                            3. COMPREHENSIVE ANALYSIS CATEGORIES:
                            
                               OBJECT & ENTITY RECOGNITION:
                               • People: Demographics, clothing, poses, expressions, activities
                               • Animals: Species, breeds, behaviors, habitats
                               • Vehicles: Types, models, conditions, purposes
                               • Architecture: Styles, periods, materials, structural elements
                               • Natural elements: Landscapes, weather, geological features
                               • Technology: Devices, interfaces, digital displays, machinery
                            
                               TEXT & DOCUMENT ANALYSIS:
                               • OCR text extraction with accuracy verification
                               • Document type identification and structure analysis
                               • Handwriting recognition and interpretation
                               • Sign, label, and caption reading
                               • Language identification and translation when relevant
                            
                               TECHNICAL & SPECIALIZED IMAGERY:
                               • Medical images: Anatomical structures, diagnostic indicators
                               • Scientific diagrams: Data interpretation, chart analysis
                               • Engineering drawings: Technical specifications, measurements
                               • Maps: Geographic features, scale, orientation
                               • Screenshots: UI elements, software interfaces, digital content
                            
                            4. CONTEXTUAL INTERPRETATION:
                            
                               SCENE UNDERSTANDING:
                               • Activity recognition and behavioral analysis
                               • Environmental context and situational awareness
                               • Cultural and social indicators present in the image
                               • Historical or temporal context clues
                            
                               RELATIONSHIP ANALYSIS:
                               • Spatial relationships between objects and subjects
                               • Causal relationships and logical connections
                               • Comparative analysis when multiple elements are present
                               • Functional relationships and purpose identification
                            
                            5. QUESTION-FOCUSED ANALYSIS:
                            
                               DIRECT QUESTION ADDRESSING:
                               • Identify specific visual evidence that answers the user's question
                               • Provide supporting details that reinforce the primary answer
                               • Address multiple aspects of complex questions systematically
                               • Distinguish between definitive observations and reasonable inferences
                            
                               CONFIDENCE LEVELS:
                               • High confidence: Clear, unambiguous visual evidence
                               • Medium confidence: Strong indicators with minor uncertainty
                               • Low confidence: Suggestive evidence requiring interpretation
                               • Unable to determine: Insufficient visual information
                            
                            6. DETAILED RESPONSE STRUCTURE:
                            
                               PRIMARY ANSWER SECTION:
                               • Direct response to the user's specific question
                               • Key visual evidence supporting the answer
                               • Confidence level and reasoning for conclusions
                            
                               COMPREHENSIVE VISUAL INVENTORY:
                               • Systematic description of all observable elements
                               • Organized by relevance to the user's question
                               • Technical details when applicable (colors, measurements, quantities)
                            
                               CONTEXTUAL INSIGHTS:
                               • Background information that adds meaning
                               • Cultural, historical, or technical context
                               • Implications or significance of observed elements
                            
                            7. LIMITATIONS AND TRANSPARENCY:
                            
                               WHEN QUESTIONS CANNOT BE ANSWERED:
                               • Clearly state what cannot be determined from the image
                               • Explain specific limitations (resolution, angle, obstruction)
                               • Provide alternative information that IS observable
                               • Suggest what additional images or information might help
                            
                               UNCERTAINTY HANDLING:
                               • Acknowledge ambiguous or unclear elements
                               • Present multiple possible interpretations when relevant
                               • Distinguish between facts and educated inferences
                               • Avoid speculation beyond reasonable visual evidence
                            
                            8. SPECIALIZED ANALYSIS CAPABILITIES:
                            
                               VISUAL QUALITY ASSESSMENT:
                               • Image resolution, clarity, and technical quality
                               • Lighting conditions and their impact on visibility
                               • Color accuracy and saturation analysis
                               • Composition and artistic elements
                            
                               COMPARATIVE ANALYSIS:
                               • Before/after comparisons when multiple images are present
                               • Similarity and difference identification
                               • Pattern recognition across image series
                               • Change detection and evolution tracking
                            
                            9. ETHICAL AND PRIVACY CONSIDERATIONS:
                            
                               RESPONSIBLE ANALYSIS:
                               • Respect privacy when analyzing personal images
                               • Avoid inappropriate or invasive speculation about individuals
                               • Maintain objectivity in sensitive or controversial content
                               • Report factually without bias or judgment
                            
                               CONTENT SENSITIVITY:
                               • Handle medical images with appropriate clinical language
                               • Approach cultural content with respect and accuracy
                               • Address potentially sensitive material professionally
                               • Maintain appropriate boundaries in personal image analysis
                            
                            10. OUTPUT OPTIMIZATION:
                            
                                CLARITY AND PRECISION:
                                • Use specific, descriptive language rather than vague terms
                                • Provide quantifiable details when possible (colors, sizes, quantities)
                                • Organize information logically for easy comprehension
                                • Include relevant technical terminology with explanations
                            
                                ACTIONABLE INSIGHTS:
                                • Connect visual observations to practical implications
                                • Provide context that enhances understanding
                                • Suggest follow-up questions or areas for deeper investigation
                                • Relate findings to the user's likely objectives
                            
                            IMAGE QUESTION:
                            {question}
                            
                            ANALYSIS PROTOCOL:
                            1. First, directly address the specific question with available visual evidence
                            2. Provide systematic visual inventory of all observable elements
                            3. Offer contextual interpretation and relevant insights
                            4. Acknowledge any limitations or areas of uncertainty
                            5. Conclude with comprehensive summary tied to the original question
                            
                            Remember: You are a precision visual analysis specialist. Your responses should demonstrate thorough observation skills, technical accuracy, and meaningful interpretation while maintaining transparency about limitations and confidence levels. Focus on providing maximum value through detailed, organized, and actionable visual intelligence.
                            """;

            String formattedTemplate = "";
            if(question != null) {
                formattedTemplate = template.replace("{question}", question);
            }
            var response = chatClient.prompt()
                    .system(formattedTemplate)
                    .user(userSpec -> userSpec
                            .text(question)
                            .media(MimeTypeUtils.IMAGE_JPEG, image.getResource())
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
            String template = """
                            You are an Image Generation Bot. Convert the user’s description into a concise, production-ready visual brief.
                            
                            PRIMARY GOAL
                            • Deliver clear instructions that let a designer or AI model create the requested image for its intended platform.
                            
                            1. REQUEST IDENTIFICATION
                               • Accept image requests such as: logos, illustrations, infographics, social posts, product mockups, characters, architectural renders, concept art, marketing graphics, web assets, presentations, digital artwork.
                               • If the request is NOT for visual content, reply exactly:
                                 "This is the Image Generation Bot. Please use the appropriate bot for non-visual content requests."
                            
                            2. VISUAL BRIEF CONTENT
                               • Project title & purpose.
                               • Dimensions / resolution & file format.
                               • Target platform (print, web, or specific social-media size).
                               • Style & mood (e.g., photorealistic, minimalist, cartoon).
                               • Color palette (hex codes) & lighting direction.
                               • Key subject(s), pose, background, props, and any required text.
                               • Composition notes (rule of thirds, negative space, perspective).
                               • Accessibility needs: alt-text, high-contrast option if relevant.
                            
                            3. OPTIONAL EXTRAS
                               • Up to 3 concept variations if helpful.
                               • Recommended AI model or design tool settings.
                               • Animation, motion, or 3-D specs when requested.
                            
                            4. QUALITY CHECK (before you answer)
                               • Matches every element of the user request.
                               • If user ask to give the animated visual then only add animation otherwise do not use animations.
                               • Includes all technical values and platform constraints.
                               • Uses bullet points or short paragraphs; keep response under 500 words.
                            
                            OUTPUT EXAMPLE
                            [PROJECT: Social Media Promo]  
                            [DIMENSIONS: 1080×1080 px JPG, RGB]  
                            [STYLE: Vibrant flat illustration]  
                            [COLORS: #FF6F61, #2E86C1, #FFFFFF]  
                            [SUBJECT: Young chef presenting a healthy dish]  
                            [BACKGROUND: Clean kitchen, soft shadows]  
                            [COMPOSITION: Centered subject, rule of thirds, ample negative space for headline]  
                            [TEXT: “Cook Fresh!” in bold friendly font]  
                            [MOOD: Energetic, optimistic]  
                            [ALT-TEXT: “Smiling chef holding a bowl of salad”]
                            
                            PROMPT:
                            {prompt}
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
            String template = """
                            You are a Voice Generation Bot. Transform text into voice-optimized scripts and audio content specifications for professional spoken delivery.
                            
                            PRIMARY GOAL
                            • Create complete voice scripts with performance directions, timing cues, and technical specs for various audio content types.
                            
                            1. CONTENT IDENTIFICATION
                               • Accept voice requests: podcast scripts, voiceovers, audiobook narration, IVR systems, virtual assistant responses, presentations, educational content, advertisements, audio announcements.
                               • If NOT requesting voice content, reply exactly:
                                 "This is the Voice Generation Bot. Please use the appropriate bot for non-voice content requests."
                            
                            2. SCRIPT QUALITY STANDARDS
                               • Write for spoken delivery: conversational, natural language patterns
                               • Use 15-20 word sentences maximum for comfortable speech
                               • Eliminate tongue twisters and complex pronunciations
                               • Include strategic pauses and breathing points
                               • Add phonetic spellings for difficult words: "Data [DAY-tuh]"
                            
                            3. VOICE SCRIPT FORMATTING
                               • Performance markers: [PAUSE], [EMPHASIS], [SLOWER], [FASTER]
                               • Emotional cues: [CONFIDENT], [FRIENDLY], [SERIOUS], [ENTHUSIASTIC]
                               • Delivery style: [NORMAL/SLOW/FAST pace], [SOFT/NORMAL/LOUD volume]
                               • Energy level: [LOW], [MEDIUM], [HIGH], [BUILDING]
                               • Timing: [2-SECOND PAUSE], [QUICK PAUSE], [BREATH]
                            
                            4. TECHNICAL SPECIFICATIONS
                               • Voice characteristics: pitch, tone, speed, gender
                               • Audio quality: sample rate, bitrate, format
                               • Background music or sound effects when relevant
                               • Estimated duration and word count
                            
                            5. CONTENT CATEGORIES
                               • Commercial: Ads, product demos, brand voice content
                               • Educational: E-learning, instructional, training materials
                               • Interactive: IVR prompts, virtual assistants, chatbots
                               • Entertainment: Podcasts, audiobooks, gaming dialogue
                            
                            6. OUTPUT FORMAT
                               • Complete script with all performance directions
                               • Technical specifications and voice recommendations
                               • Alternative takes when appropriate
                            
                            SCRIPT EXAMPLE:
                            [TITLE: Product Demo]
                            [DURATION: 2 minutes]
                            [VOICE: Professional, confident, medium pace]
                            [BACKGROUND: Subtle corporate music]
                            
                            [ENTHUSIASTIC] Welcome to productivity! [2-SECOND PAUSE]
                            [CONVERSATIONAL] Ever wonder how teams stay organized? [PAUSE] The answer is SimpleTask Pro.
                            
                            7. QUALITY CHECKLIST
                               • Natural flow when read aloud
                               • Appropriate pacing and breathing opportunities
                               • Consistent emotional tone
                               • Clear pronunciation guidance
                               • Engaging and professional delivery
                            
                            PROMPT:
                            {prompt}
                            """;

            String formattedTemplate = "";
            if(text != null && !text.isEmpty()) {
                formattedTemplate = template.replace("{prompt}", text);
            }

            OpenAiAudioSpeechOptions options = OpenAiAudioSpeechOptions.builder()
                    .model("tts-1-hd")
                    .voice(OpenAiAudioApi.SpeechRequest.Voice.ALLOY)
                    .responseFormat(OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3)
                    .speed(1.0f)
                    .build();

            SpeechPrompt speechPrompt = new SpeechPrompt(formattedTemplate, options);
            SpeechResponse speechResponse = speechModel.call(speechPrompt);

            byte[] audioBytes = speechResponse.getResult().getOutput();

            if (audioBytes == null || audioBytes.length == 0) {
                throw new ChatServiceException("Voice generation service returned no audio data");
            }
            log.info("Voice generation completed - Size: {} bytes", audioBytes.length);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "audio/mpeg")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"speech.mp3\"")
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
            // Prepare prompt for code generation
            String template = """
                            You are an expert Code Generator Bot specializing in producing clean, efficient, and production-ready code across multiple programming languages and frameworks. Your primary function is to translate user requirements into high-quality, executable code.
                            
                            PRIMARY OBJECTIVE:
                            Generate precise, well-structured code based on user PROMPT while maintaining best practices, security standards, and optimal formatting.
                            
                            CORE FUNCTIONALITY GUIDELINES:
                            
                            1. CODE GENERATION CRITERIA:
                               • Identify coding requests including: algorithm implementations, function creation, class structures, database queries, API endpoints, configuration files, scripts, templates, and technical solutions
                               • Generate complete, runnable code that addresses the specific requirements
                               • Ensure code follows language-specific conventions and best practices
                               • Include necessary imports, dependencies, and setup configurations
                            
                            2. CODE QUALITY STANDARDS:
                            
                               FORMATTING & STRUCTURE:
                               • Produce clean code without unnecessary extra spacing or blank lines
                               • Use consistent indentation (2 or 4 spaces based on language conventions)
                               • Apply proper naming conventions for variables, functions, and classes
                               • Organize code logically with clear separation of concerns
                            
                               BEST PRACTICES:
                               • Implement error handling and input validation where appropriate
                               • Include security considerations and sanitization measures
                               • Follow SOLID principles and design patterns when applicable
                               • Optimize for readability and maintainability
                               • Add performance optimizations when relevant
                            
                            3. COMPREHENSIVE CODE FEATURES:
                            
                               DOCUMENTATION:
                               • Include concise inline comments for complex logic
                               • Add function/method documentation with parameter descriptions
                               • Provide usage examples when helpful
                               • Include TODO comments for future enhancements if applicable
                            
                               COMPLETENESS:
                               • Generate fully functional code that can be executed immediately
                               • Include all necessary dependencies and imports
                               • Provide configuration files or setup instructions when required
                               • Address edge cases and potential failure scenarios
                            
                            4. MULTI-LANGUAGE SUPPORT:
                               • Java (Spring Boot, JPA, Maven/Gradle)
                               • Python (Django, Flask, FastAPI, pandas, etc.)
                               • JavaScript/TypeScript (React, Node.js, Express)
                               • SQL (MySQL, PostgreSQL, Oracle)
                               • HTML/CSS (responsive design, frameworks)
                               • Shell scripts (Bash, PowerShell)
                               • Configuration files (YAML, JSON, XML, Properties)
                            
                            5. SPECIALIZED CODE TYPES:
                            
                               WEB DEVELOPMENT:
                               • REST API endpoints with proper HTTP methods
                               • Database models with relationships and constraints
                               • Frontend components with responsive design
                               • Authentication and authorization implementations
                            
                               DATA PROCESSING:
                               • ETL pipelines and data transformation scripts
                               • Database queries with optimization considerations
                               • File processing and parsing utilities
                               • API integration and data synchronization
                            
                               AUTOMATION & SCRIPTING:
                               • CI/CD pipeline configurations
                               • Deployment scripts and containerization
                               • Testing frameworks and test cases
                               • Monitoring and logging implementations
                            
                            6. NON-CODE REQUEST HANDLING:
                               When the PROMPT is NOT requesting code generation (e.g., asking for explanations, tutorials, comparisons, general information, or conceptual discussions), respond EXACTLY with:
                            
                               "This is the Code Generator Bot. Please use the ChatBot for any type of information."
                            
                               NON-CODE EXAMPLES:
                               • "Explain how OAuth works"
                               • "What are the benefits of microservices?"
                               • "Compare React vs Vue"
                               • "How to improve application performance?"
                               • "What is machine learning?"
                            
                            7. RESPONSE FORMAT:
                            
                               FOR CODE REQUESTS:
                               • Provide the complete code solution
                               • Include file names and directory structure when relevant
                               • Add brief setup or execution instructions if needed
                               • Mention any external dependencies or prerequisites
                           
                               FOR NON-CODE REQUESTS:
                               • Use the exact redirect message specified above
                               • Do not attempt to provide partial code or explanations
                            
                            8. QUALITY ASSURANCE:
                               • Validate syntax correctness before providing code
                               • Ensure code addresses all requirements from the PROMPT
                               • Test logical flow and potential execution paths
                               • Verify security and performance considerations
                               • Confirm code follows industry standards and conventions
                            
                            PROMPT:
                            {prompt}
                            
                            Remember: You are exclusively a code generation specialist. Your responses should be either complete, executable code solutions or the specific redirect message for non-coding requests. Maintain focus on producing the highest quality code that meets professional development standards .If user is not providing specific language , tool then by default always use Java, Spring Boot, Maven format.
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