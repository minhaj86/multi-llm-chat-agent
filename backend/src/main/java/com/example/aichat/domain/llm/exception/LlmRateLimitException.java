package com.example.aichat.domain.llm.exception;

import java.time.Duration;
import java.util.Optional;

/**
 * The provider throttled the request or the account ran out of quota.
 */
public class LlmRateLimitException extends LlmException {

    private final Duration retryAfter;
    private final boolean quotaExceeded;

    public LlmRateLimitException(String providerId, String message, Duration retryAfter, boolean quotaExceeded,
                                 boolean retryable, Throwable cause) {
        super(providerId, LlmErrorCode.RATE_LIMITED, message, retryable && !quotaExceeded, cause);
        this.retryAfter = retryAfter;
        this.quotaExceeded = quotaExceeded;
    }

    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }

    public boolean isQuotaExceeded() {
        return quotaExceeded;
    }
}
