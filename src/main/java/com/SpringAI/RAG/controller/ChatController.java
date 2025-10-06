package com.SpringAI.RAG.controller;

import com.SpringAI.RAG.dto.BlogPostResponseDTO;
import com.SpringAI.RAG.dto.WebDataRequest;
import com.SpringAI.RAG.service.ChatService;
import com.SpringAI.RAG.service.WebDataService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
   private final WebDataService webDataService;

    public ChatController(ChatService chatService, WebDataService webDataService) {
        this.chatService = chatService;
        this.webDataService = webDataService;
    }

    @PostMapping("/pdfStore")
    @Operation(summary = "Initialize the vector_store with PDF data", description = "Uploads a PDF file and processes it into the vector store.")
    public ResponseEntity<String> initializeVectorStore(@RequestParam("file") MultipartFile file) {
        chatService.initializeVectorStore(file);
        return ResponseEntity.ok("Vector store initialized with the PDF data successfully.");
    }

    @PostMapping("/chatBot")
    @Operation(summary = "Query the chatBot", description = "Send a query to the chatbot and get a response.")
    public ResponseEntity<String> queryChat(@RequestParam("message") String message) {
        return chatService.chatBotForVectorStore(message);
    }

    @PostMapping("/blogGenerationBot")
    @Operation(summary = "Query the blogGenerationBot", description = "Send a query to the blogGenerationBot and get a response.")
    public BlogPostResponseDTO blogGenerationBot(@RequestParam("message") String message) {
        return chatService.blogPostBot(message);
    }

    @PostMapping(value = "/imageDetectionBot", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Query the imageDetectionBot", description = "Send a query to the imageDetectionBot and get a response.")
    public ResponseEntity<String> imageDetectionBot(
            @RequestPart("file") MultipartFile file,
            @RequestPart("text") String text) {
        return chatService.ImageDetectionBot(file, text);
    }

    @PostMapping("/imageGenerationBot")
    @Operation(summary = "Query the imageGenerationBot", description = "Send a query to the imageGenerationBot and get a response.")
    public ResponseEntity<String> imageGenerationBot(@RequestParam("message") String message) {
        return chatService.ImageGenerationBot(message);
    }

    @PostMapping("/voiceGenerationBot")
    @Operation(summary = "Query the voiceGenerationBot", description = "Send a query to the voiceGenerationBot and get a response.")
    public ResponseEntity<byte[]> voiceGenerationBot(@RequestParam("message") String message) {
        return chatService.VoiceGenerationBot(message);
    }

    @PostMapping("/codeBot")
    @Operation(summary = "Query the codeGenerationBot to generate code", description = "Send a prompt to the codeGenerationBot to generate code")
    public ResponseEntity<String> codeGenerationBot(@RequestParam("prompt") String prompt) {
        String code = chatService.codeGeneratorBot(prompt);
        return ResponseEntity.ok(code);
    }

    @PostMapping("/crawlWeb/store")
    @Operation(summary = "Crawl a website and store content in vector_store",
            description = "Crawl a website and store the extracted content")
    public ResponseEntity<String> crawlAndStoreContent(@RequestBody WebDataRequest request) throws IOException {
        List<String> contentList = webDataService.crawlAndExtractContent(request.getUrl());
        webDataService.storeContent(contentList);
        return ResponseEntity.ok("Content crawled and stored successfully.");
    }

    @PostMapping("/query/webContent")
    @Operation(summary = "Search for relevant content in vector_store based on the query",
            description = "Search for content in the stored data and provide a relevant response")
    public ResponseEntity<String> queryContent(@RequestBody WebDataRequest request) {
        String response = webDataService.queryContent(request.getQuery());
        return ResponseEntity.ok(response);
    }
}