package com.example.aichat.application.provider;

/**
 * No provider with the given id is configured.
 */
public class UnknownProviderException extends RuntimeException {

    private final String providerId;

    public UnknownProviderException(String providerId) {
        super("Unknown LLM provider: " + providerId);
        this.providerId = providerId;
    }

    public String providerId() {
        return providerId;
    }
}
