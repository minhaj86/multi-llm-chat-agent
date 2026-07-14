package com.example.aichat.application.chat.context;

import com.example.aichat.domain.llm.LlmMessage;
import java.util.List;

/**
 * Provider-neutral messages ready to be sent, plus diagnostics about what was included.
 */
public record PreparedContext(List<LlmMessage> messages,
                              ContextStrategy strategy,
                              int estimatedTokens,
                              int includedHistoryMessages,
                              int omittedHistoryMessages,
                              boolean summaryIncluded) {

    public PreparedContext {
        messages = List.copyOf(messages);
    }
}
