package com.example.aichat.domain.llm.exception;

/**
 * The provider did not answer in time. Never retried automatically: the provider may still be generating
 * (and billing) the response.
 */
public class LlmTimeoutException extends LlmException {

    public LlmTimeoutException(String providerId, String message, Throwable cause) {
        super(providerId, LlmErrorCode.TIMEOUT, message, false, cause);
    }

    @Override
    public boolean indicatesProviderFailure() {
        return true;
    }
}
