package com.SpringAI.RAG.controller;

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

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/initialize/vector")
    @Operation(summary = "Initialize the vector store with PDF data", description = "Uploads a PDF file and processes it into the vector store.")
    public ResponseEntity<String> initializeVectorStore(
            @Parameter(description = "PDF file resource to upload") @RequestParam("file") MultipartFile file) {
        try {
            chatService.initializeVectorStore(file);
            return ResponseEntity.ok("Vector store initialized with the PDF data successfully.");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error initializing vector store: " + e.getMessage());
        }
    }

    @PostMapping("/question")
    @Operation(summary = "Query the chatbot", description = "Send a query to the chatbot and get a response.")
    public ResponseEntity<String> queryChat(
            @Parameter(description = "Message to ask the chatbot") @RequestBody String message) {
        String response = chatService.handleQuery(message);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/generateCode")
    @Operation(summary = "Query the chatbot for code", description = "Send a prompt to the chatbot to generate code")
    public ResponseEntity<String> generateCode(@RequestBody String prompt) {
        try {
            String code = chatService.generateCode(prompt);
            return ResponseEntity.ok(code);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error generating code: " + e.getMessage());
        }
    }
}
