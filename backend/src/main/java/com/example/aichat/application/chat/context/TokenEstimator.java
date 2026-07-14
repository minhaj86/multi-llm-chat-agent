package com.example.aichat.application.chat.context;

import com.example.aichat.domain.llm.LlmMessage;

/**
 * Estimates token counts. The default implementation is a provider-independent heuristic; a tokenizer-backed
 * implementation can be plugged in without touching the context manager.
 */
public interface TokenEstimator {

    /** Per-message overhead for role markers and separators added by chat templates. */
    int MESSAGE_OVERHEAD = 4;

    int estimate(String text);

    default int estimate(LlmMessage message) {
        return estimate(message.content()) + MESSAGE_OVERHEAD;
    }
}
