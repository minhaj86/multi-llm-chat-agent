package com.example.aichat.application.common;

/**
 * The request conflicts with the current state (duplicate username, deleting the last administrator, ...).
 */
public class ConflictException extends RuntimeException {

    private final String code;

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
