package com.example.aichat.application.provider;

import com.example.aichat.common.Texts;
import com.example.aichat.domain.llm.exception.LlmException;
import com.example.aichat.domain.llm.exception.LlmRateLimitException;

/**
 * Turns provider-neutral failures into messages that end users understand. Raw upstream payloads are never shown
 * for authentication problems (they may contain key fragments).
 */
public final class LlmErrorMessages {

    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    private LlmErrorMessages() {
    }

    public static String code(Throwable error) {
        return error instanceof LlmException llm ? llm.code().name() : INTERNAL_ERROR;
    }

    public static String describe(Throwable error, String providerName) {
        String name = Texts.hasText(providerName) ? providerName : "The provider";
        if (!(error instanceof LlmException llm)) {
            return "Unexpected error while generating the response.";
        }
        String detail = Texts.truncate(Texts.singleLine(llm.getMessage()), 300);
        return switch (llm.code()) {
            case PROVIDER_UNAVAILABLE -> name + " is currently unavailable.";
            case AUTHENTICATION_FAILED -> (Texts.hasText(detail) ? detail : name + " rejected the credentials")
                    + ". Ask an administrator to check the provider credentials.";
            case RATE_LIMITED -> llm instanceof LlmRateLimitException rate && rate.isQuotaExceeded()
                    ? name + " quota has been exceeded."
                    : name + " is rate limiting requests. Please try again in a moment.";
            case TIMEOUT -> name + " did not respond in time.";
            case MODEL_NOT_FOUND -> "The selected model is not available on " + name + ". " + detail;
            case INVALID_REQUEST -> name + " rejected the request: " + detail;
            case CONTENT_FILTERED -> "The response was blocked by " + name + "'s content filter.";
            case STREAM_INTERRUPTED -> "The response from " + name + " was interrupted.";
            case UPSTREAM_ERROR -> name + " returned an error. " + detail;
        };
    }

    public static boolean retryable(Throwable error) {
        if (!(error instanceof LlmException llm)) {
            return false;
        }
        return switch (llm.code()) {
            case PROVIDER_UNAVAILABLE, RATE_LIMITED, TIMEOUT, STREAM_INTERRUPTED, UPSTREAM_ERROR -> true;
            default -> false;
        };
    }
}
