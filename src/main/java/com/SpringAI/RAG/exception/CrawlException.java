package com.SpringAI.RAG.exception;

public class CrawlException extends RuntimeException {
    public CrawlException(String message, Throwable cause) {
        super(message, cause);
    }
}