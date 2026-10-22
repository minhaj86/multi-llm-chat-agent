package com.example.aichat.application.chat;

import com.example.aichat.domain.conversation.KnowledgeSettings;
import com.example.aichat.domain.conversation.ConversationType;
import com.example.aichat.domain.conversation.AudioSettings;
import java.util.UUID;

/**
 * Send a user message and stream the answer.
 *
 * @param conversationId existing conversation, or {@code null} to start a new one
 * @param provider       provider id ({@code null} = default provider)
 * @param model          model id ({@code null} = provider default model)
 * @param systemPromptId system prompt profile for a new conversation, or a change for an existing one
 */
public record ChatCommand(UUID userId,
                          UUID conversationId,
                          String message,
                          String provider,
                          String model,
                          UUID systemPromptId,
                          String correlationId,
                          KnowledgeSettings knowledge,
                          ConversationType type,
                          AudioSettings audio) {

    public ChatCommand(UUID userId, UUID conversationId, String message, String provider, String model,
                       UUID systemPromptId, String correlationId, KnowledgeSettings knowledge) {
        this(userId, conversationId, message, provider, model, systemPromptId, correlationId, knowledge, null, null);
    }

    /** Without a knowledge selection: the conversation keeps its current knowledge settings. */
    public ChatCommand(UUID userId, UUID conversationId, String message, String provider, String model,
                       UUID systemPromptId, String correlationId) {
        this(userId, conversationId, message, provider, model, systemPromptId, correlationId, null);
    }
}
