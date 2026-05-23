package com.example.aichat.domain.llm.exception;

/**
 * The provider cannot be reached (connection refused, DNS failure, 502/503/504, open circuit breaker, ...).
 */
public class LlmProviderUnavailableException extends LlmException {

    public LlmProviderUnavailableException(String providerId, String message, boolean retryable, Throwable cause) {
        super(providerId, LlmErrorCode.PROVIDER_UNAVAILABLE, message, retryable, cause);
    }

    public LlmProviderUnavailableException(String providerId, String message) {
        this(providerId, message, false, null);
    }

    @Override
    public boolean indicatesProviderFailure() {
        return true;
    }
}
