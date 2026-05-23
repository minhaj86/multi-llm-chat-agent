package com.example.aichat.domain.llm.exception;

/**
 * Stable, provider-neutral error codes exposed to API clients.
 */
public enum LlmErrorCode {
    PROVIDER_UNAVAILABLE,
    AUTHENTICATION_FAILED,
    RATE_LIMITED,
    TIMEOUT,
    INVALID_REQUEST,
    MODEL_NOT_FOUND,
    CONTENT_FILTERED,
    UPSTREAM_ERROR,
    STREAM_INTERRUPTED
}
