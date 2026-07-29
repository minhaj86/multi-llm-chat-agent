package com.example.aichat.application.chat;

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
                          String correlationId) {
}
