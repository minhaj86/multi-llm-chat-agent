package com.example.aichat.domain.llm.exception;

import java.util.Set;

/**
 * The provider answered with an error or an unusable payload (invalid request, unknown model, content filter,
 * internal server error, malformed/interrupted stream, ...).
 */
public class LlmResponseException extends LlmException {

    private static final Set<LlmErrorCode> ALLOWED = Set.of(
            LlmErrorCode.INVALID_REQUEST,
            LlmErrorCode.MODEL_NOT_FOUND,
            LlmErrorCode.CONTENT_FILTERED,
            LlmErrorCode.UPSTREAM_ERROR,
            LlmErrorCode.STREAM_INTERRUPTED);

    private final Integer httpStatus;

    public LlmResponseException(String providerId, LlmErrorCode code, String message, Integer httpStatus, Throwable cause) {
        super(providerId, requireAllowed(code), message, false, cause);
        this.httpStatus = httpStatus;
    }

    public LlmResponseException(String providerId, LlmErrorCode code, String message) {
        this(providerId, code, message, null, null);
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    @Override
    public boolean indicatesProviderFailure() {
        return code() == LlmErrorCode.UPSTREAM_ERROR || code() == LlmErrorCode.STREAM_INTERRUPTED;
    }

    private static LlmErrorCode requireAllowed(LlmErrorCode code) {
        if (!ALLOWED.contains(code)) {
            throw new IllegalArgumentException("Unsupported code for LlmResponseException: " + code);
        }
        return code;
    }
}
