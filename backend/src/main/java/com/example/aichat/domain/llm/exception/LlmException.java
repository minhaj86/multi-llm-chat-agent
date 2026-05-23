package com.example.aichat.domain.llm.exception;

/**
 * Base class of all provider-neutral LLM failures. Adapters map vendor specific failures (HTTP status codes,
 * error payloads, transport exceptions) into subclasses of this exception.
 *
 * <p>{@link #isRetryable()} tells the resilience layer whether a retry is safe, i.e. the request has most likely
 * not been processed (and billed) by the provider. Once a stream has emitted data, the failure is marked
 * non-retryable to avoid duplicated output and double billing.
 */
public abstract class LlmException extends RuntimeException {

    private final String providerId;
    private final LlmErrorCode code;
    private volatile boolean retryable;

    protected LlmException(String providerId, LlmErrorCode code, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.providerId = providerId;
        this.code = code;
        this.retryable = retryable;
    }

    public String providerId() {
        return providerId;
    }

    public LlmErrorCode code() {
        return code;
    }

    public boolean isRetryable() {
        return retryable;
    }

    /** Marks this failure as unsafe to retry (e.g. because a stream already produced output). */
    public LlmException nonRetryable() {
        this.retryable = false;
        return this;
    }

    /** Whether this failure indicates that the provider itself is unhealthy (used by circuit breakers). */
    public boolean indicatesProviderFailure() {
        return false;
    }
}
