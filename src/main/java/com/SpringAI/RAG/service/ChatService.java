package com.SpringAI.RAG.service;

import org.springframework.web.multipart.MultipartFile;

public interface ChatService {

    /**
     * Initializes the vector store with the content of a PDF.
     *
     * @param file The PDF file to be processed.
     */
    void initializeVectorStore(MultipartFile file);

    /**
     * Handles a query by searching the vector store and generating a response from the chatbot.
     *
     * @param question The query to be asked to the chatbot.
     * @return The chatbot's response.
     */
    String handleQuery(String question);

    /**
     * Generates code based on the provided prompt.
     *
     * @param prompt The prompt that describes the code to be generated.
     * @return The generated code.
     */
    String generateCode(String prompt);
}