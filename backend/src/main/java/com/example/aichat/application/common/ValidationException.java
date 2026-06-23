package com.example.aichat.application.common;

/**
 * Input that passed syntactic validation but violates a business rule (reported as 400).
 */
public class ValidationException extends RuntimeException {

    private final String code;

    public ValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
