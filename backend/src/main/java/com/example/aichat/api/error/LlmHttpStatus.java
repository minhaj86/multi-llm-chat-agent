package com.example.aichat.api.error;

import com.example.aichat.domain.llm.exception.LlmErrorCode;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * HTTP status for provider-neutral LLM error codes.
 */
public final class LlmHttpStatus {

    private static final Map<String, HttpStatus> STATUS_BY_CODE = Map.of(
            LlmErrorCode.PROVIDER_UNAVAILABLE.name(), HttpStatus.SERVICE_UNAVAILABLE,
            LlmErrorCode.AUTHENTICATION_FAILED.name(), HttpStatus.BAD_GATEWAY,
            LlmErrorCode.RATE_LIMITED.name(), HttpStatus.TOO_MANY_REQUESTS,
            LlmErrorCode.TIMEOUT.name(), HttpStatus.GATEWAY_TIMEOUT,
            LlmErrorCode.INVALID_REQUEST.name(), HttpStatus.BAD_REQUEST,
            LlmErrorCode.MODEL_NOT_FOUND.name(), HttpStatus.BAD_REQUEST,
            LlmErrorCode.CONTENT_FILTERED.name(), HttpStatus.UNPROCESSABLE_ENTITY,
            LlmErrorCode.UPSTREAM_ERROR.name(), HttpStatus.BAD_GATEWAY,
            LlmErrorCode.STREAM_INTERRUPTED.name(), HttpStatus.BAD_GATEWAY);

    private LlmHttpStatus() {
    }

    public static HttpStatus forCode(String code) {
        return STATUS_BY_CODE.getOrDefault(code, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
