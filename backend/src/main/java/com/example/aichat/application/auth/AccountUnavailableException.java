package com.example.aichat.application.auth;

/**
 * The account is disabled or temporarily locked.
 */
public class AccountUnavailableException extends RuntimeException {

    private final String code;

    public AccountUnavailableException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
