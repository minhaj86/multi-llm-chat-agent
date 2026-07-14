package com.example.aichat.application.chat.context;

import com.example.aichat.domain.conversation.Conversation;
import com.example.aichat.domain.conversation.Message;
import java.util.List;

/**
 * Input of the context manager.
 *
 * @param systemPrompt system prompt text ({@code null} for none); always kept separate from the history
 * @param conversation conversation (provides the rolling summary)
 * @param history      previous messages in chronological order, excluding {@code userMessage}
 * @param userMessage  the message that must be answered now
 */
public record ContextInput(String systemPrompt, Conversation conversation, List<Message> history, String userMessage) {

    public ContextInput {
        history = history == null ? List.of() : List.copyOf(history);
        userMessage = userMessage == null ? "" : userMessage;
    }
}
