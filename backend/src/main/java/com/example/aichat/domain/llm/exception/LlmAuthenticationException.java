package com.example.aichat.domain.llm.exception;

/**
 * The provider rejected the configured credentials (missing/invalid API key, insufficient permissions).
 */
public class LlmAuthenticationException extends LlmException {

    public LlmAuthenticationException(String providerId, String message, Throwable cause) {
        super(providerId, LlmErrorCode.AUTHENTICATION_FAILED, message, false, cause);
    }

    public LlmAuthenticationException(String providerId, String message) {
        this(providerId, message, null);
    }
}
