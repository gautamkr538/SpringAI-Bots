package com.SpringAI.RAG.exception;

public class ContentExtractionException extends RuntimeException {
    public ContentExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}