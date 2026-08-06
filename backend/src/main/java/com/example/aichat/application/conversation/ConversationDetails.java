package com.example.aichat.application.conversation;

import com.example.aichat.domain.conversation.Conversation;
import com.example.aichat.domain.conversation.Message;
import com.example.aichat.domain.usage.LlmExecution;
import java.util.List;

/**
 * A conversation with its messages; assistant messages carry the execution metrics that produced them.
 */
public record ConversationDetails(Conversation conversation, List<MessageView> messages) {

    public record MessageView(Message message, LlmExecution execution) {
    }
}
