package com.SpringAI.RAG.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ErrorResponse {

    private String message;
    private int statusCode;

    public String getMessageSafe() {
        return message != null ? message : "[No error message]";
    }
}