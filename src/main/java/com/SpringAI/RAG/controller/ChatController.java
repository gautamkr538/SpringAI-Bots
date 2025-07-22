package com.SpringAI.RAG.controller;

import com.SpringAI.RAG.dto.BlogPostResponseDTO;
import com.SpringAI.RAG.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
   /*private final WebDataService webDataService;*/

    public ChatController(ChatService chatService
            /*, WebDataService webDataService*/) {
        this.chatService = chatService;
        /*this.webDataService = webDataService;*/
    }

    @PostMapping("/pdfStore")
    @Operation(summary = "Initialize the vector_store with PDF data",
            description = "Uploads a PDF file and processes it into the vector store.")
    public ResponseEntity<String> initializeVectorStore(
            @Parameter(description = "PDF file resource to upload") @RequestParam("file") MultipartFile file) {
        chatService.initializeVectorStore(file);
        return ResponseEntity.ok("Vector store initialized with the PDF data successfully.");
    }

    @PostMapping("/chatBot")
    @Operation(summary = "Query the chatBot", description = "Send a query to the chatbot and get a response.")
    public ResponseEntity<String> queryChat(@Parameter(description = "Message to ask the chatbot") @RequestBody String message) {
        return chatService.chatBotForVectorStore(message);
    }

    @PostMapping("/blogGenerationBot")
    @Operation(summary = "Query the blogGenerationBot", description = "Send a query to the blogGenerationBot and get a response.")
    public BlogPostResponseDTO blogGenerationBot(@Parameter(description = "Message to ask the blogGenerationBot") @RequestBody String message) {
        return chatService.blogPostBot(message);
    }

    @PostMapping("/imageDetectionBot")
    @Operation(summary = "Query the imageDetectionBot", description = "Send a query to the imageDetectionBot and get a response.")
    public ResponseEntity<String> imageDetectionBot(@Parameter(description = "Message to ask the imageDetectionBot") @RequestBody MultipartFile File, String Text) {
        return chatService.ImageDetectionBot(File, Text);
    }

    @PostMapping("/imageGenerationBot")
    @Operation(summary = "Query the imageGenerationBot", description = "Send a query to the imageGenerationBot and get a response.")
    public ResponseEntity<String> imageGenerationBot(@Parameter(description = "Message to ask the imageGenerationBot") @RequestBody String message) {
        return chatService.ImageGenerationBot(message);
    }

    @PostMapping("/voiceGenerationBot")
    @Operation(summary = "Query the voiceGenerationBot", description = "Send a query to the voiceGenerationBot and get a response.")
    public ResponseEntity<byte[]> voiceGenerationBot(@Parameter(description = "Message to ask the voiceGenerationBot") @RequestBody String message) {
        return chatService.VoiceGenerationBot(message);
    }

    @PostMapping("/codeBot")
    @Operation(summary = "Query the codeGenerationBot to generate code", description = "Send a prompt to the codeGenerationBot to generate code")
    public ResponseEntity<String> codeGenerationBot(@RequestBody String prompt) {
        String code = chatService.codeGeneratorBot(prompt);
        return ResponseEntity.ok(code);
    }

/*    @PostMapping("/crawlWeb/store")
    @Operation(summary = "Crawl a website and store content in vector_store",
            description = "Crawl a website and store the extracted content")
    public ResponseEntity<String> crawlAndStoreContent(@RequestBody WebDataRequest request) throws  IOException {
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
    }*/
}