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
import java.io.IOException;
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

            // Clear existing data in the vector_store database
            jdbcTemplate.update("delete from vector_store");

            // Read the uploaded PDF file
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
        } catch (IOException e) {
            log.error("Error while processing the file: ", e);
            throw new RuntimeException("Error while processing the file", e);
        } catch (Exception e) {
            log.error("Unexpected error during vector store initialization", e);
            throw new RuntimeException("Unexpected error during vector store initialization", e);
        }
    }

    @Override
    public String handleQuery(String question) {
        log.info("Received query: {}", question);

        // Retrieve similar documents from the vector store
        List<Document> similarDocuments = this.vectorStore.similaritySearch(question);

        // Combine content from similar documents
        String documents = similarDocuments.stream()
                .map(Document::getContent)
                .collect(Collectors.joining(System.lineSeparator()));

        // Define a prompt template
        String template = """
                If the information is available in the DOCUMENTS,
                respond with the relevant details as if you innately knew them.
                
                If the information is not found in the DOCUMENTS,
                clearly state: "The data is not available in the provided document. Here's my response based on my knowledge."
                
                DOCUMENTS:
                {documents}
                """;

        // Replace placeholders in the template with actual document content
        SystemMessage systemMessage = new SystemMessage(template.replace("{documents}", documents));
        UserMessage userMessage = new UserMessage(question);

        // Create a prompt with system and user messages
        Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

        // Call the chat client to generate a response
        String response = chatClient.prompt(prompt).call().content();

        log.info("Response generated: {}", response);
        return response;
    }
}