package com.example.aichat.rag.domain;

/**
 * Embedding failed. {@code retryable} is set for transient failures (connection refused, 429, 5xx), so that a later
 * retry of the ingestion can succeed; messages never contain input text.
 */
public class EmbeddingException extends RuntimeException {

    private final boolean retryable;

    public EmbeddingException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
