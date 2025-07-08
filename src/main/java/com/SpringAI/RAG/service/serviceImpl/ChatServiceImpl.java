package com.SpringAI.RAG.service.serviceImpl;

import com.SpringAI.RAG.exception.ChatServiceException;
import com.SpringAI.RAG.service.ChatService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ChatServiceImpl implements ChatService {

    private final ChatClient chatClient;

    @Autowired
    @Qualifier("customVectorStore")
    private VectorStore vectorStore;

    private final JdbcTemplate jdbcTemplate;

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    public ChatServiceImpl(ChatClient.Builder chatClientBuilder, VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.chatClient = chatClientBuilder.build();
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
    public String chatBot(String question) {
        log.info("Received query: {}", question);
        try {
            List<Document> similarDocuments = this.vectorStore.similaritySearch(question);
            assert similarDocuments != null;
            String documents = similarDocuments.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining(System.lineSeparator()));
            // Prepare prompt for code generation
            String template = """
                If the information is available in the DOCUMENTS,
                respond with the relevant details as if you innately knew them.
                If the information is not found in the DOCUMENTS,
                clearly state: "The data is not available in the provided document.
                Here's my response based on my knowledge."
                
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
    public String codeGeneratorBot(String prompt) {
        log.info("Received code generation prompt: {}", prompt);
        try {
            // Prepare prompt for code generation
            String template = """
                Based on the provided prompt, generate the corresponding code without extra spacing.
                If the prompt is not asking for code generation, clearly state:
                "This is the Code Generator Bot. Please use the ChatBot for any type of information."
    
                PROMPT:
                {prompt}
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