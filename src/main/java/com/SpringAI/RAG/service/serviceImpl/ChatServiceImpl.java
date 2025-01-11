package com.SpringAI.RAG.service.serviceImpl;

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
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.util.List;
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
                            .withNumberOfTopPagesToSkipBeforeDelete(1)
                            .build())
                    .withPagesPerDocument(1)
                    .build();
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource, config);
            // Split extracted text into tokens and store in vector_store
            TokenTextSplitter textSplitter = new TokenTextSplitter();
            vectorStore.accept(textSplitter.apply(pdfReader.get()));
            log.info("Vector store initialized successfully");
        } catch (FileNotFoundException e) {
            log.error("File not found: ", e);
            throw new RuntimeException("File not found", e);
        } catch (Exception e) {
            log.error("Unexpected error during vector store initialization", e);
            throw new RuntimeException("Unexpected error during vector_store initialization", e);
        }
    }

    @Override
    public String chatBot(String question) {
        log.info("Received query: {}", question);
        try {
            // Search for documents related to the user's query
            List<Document> similarDocuments = this.vectorStore.similaritySearch(question);
            String documents = similarDocuments.stream()
                    .map(Document::getContent)
                    .collect(Collectors.joining(System.lineSeparator()));
            // Prepare prompt with documents and question
            String template = """
                If the information is available in the DOCUMENTS,
                respond with the relevant details as if you innately knew them.
                If the information is not found in the DOCUMENTS,
                clearly state: "The data is not available in the provided document.
                Here's my response based on my knowledge."
                
                DOCUMENTS:
                {documents}
                """;
            // Format the prompt for ChatGPT
            SystemMessage systemMessage = new SystemMessage(template.replace("{documents}", documents));
            UserMessage userMessage = new UserMessage(question);
            Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
            // Get response from the chat client
            String response = chatClient.prompt(prompt).call().content();
            log.info("Response generated: {}", response);
            return response;
        } catch (Exception e) {
            log.error("Error processing the chatbot query: ", e);
            return "An error occurred while processing the query. Please try again later.";
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
                
                {prompt}
                """;
            SystemMessage systemMessage = new SystemMessage(template.replace("{prompt}", prompt));
            UserMessage userMessage = new UserMessage(prompt);
            Prompt codePrompt = new Prompt(List.of(systemMessage, userMessage));
            // Get generated code from the chat client
            String generatedCode = chatClient.prompt(codePrompt).call().content();
            log.info("Generated code: {}", generatedCode);
            return generatedCode;
        } catch (Exception e) {
            log.error("Error generating code: ", e);
            return "An error occurred while generating the code. Please try again later.";
        }
    }
}