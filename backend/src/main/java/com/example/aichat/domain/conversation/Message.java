package com.example.aichat.domain.conversation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A message of a conversation. Assistant messages record the provider and model that produced them, which allows
 * a single conversation to contain answers from several providers.
 */
public record Message(UUID id,
                      UUID conversationId,
                      MessageRole role,
                      String content,
                      String reasoning,
                      String provider,
                      String model,
                      MessageStatus status,
                      String errorCode,
                      String errorMessage,
                      Instant createdAt,
                      Instant updatedAt) {

    public static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    public Message {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(status, "status");
        content = content == null ? "" : content;
        reasoning = reasoning == null || reasoning.isBlank() ? null : reasoning;
        if (errorMessage != null && errorMessage.length() > MAX_ERROR_MESSAGE_LENGTH) {
            errorMessage = errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
        }
    }

    public static Message user(UUID conversationId, String content, Instant now) {
        return new Message(UUID.randomUUID(), conversationId, MessageRole.USER, content, null, null, null,
                MessageStatus.COMPLETE, null, null, now, now);
    }

    public static Message assistantPlaceholder(UUID conversationId, String provider, String model, Instant now) {
        return new Message(UUID.randomUUID(), conversationId, MessageRole.ASSISTANT, "", null, provider, model,
                MessageStatus.STREAMING, null, null, now, now);
    }

    public Message complete(String finalContent, String finalReasoning, String actualModel, Instant now) {
        return new Message(id, conversationId, role, finalContent, finalReasoning, provider,
                actualModel == null || actualModel.isBlank() ? model : actualModel,
                MessageStatus.COMPLETE, null, null, createdAt, now);
    }

    public Message cancel(String partialContent, String partialReasoning, Instant now) {
        return new Message(id, conversationId, role, partialContent, partialReasoning, provider, model,
                MessageStatus.CANCELLED, null, null, createdAt, now);
    }

    public Message fail(String partialContent, String partialReasoning, String code, String message, Instant now) {
        return new Message(id, conversationId, role, partialContent, partialReasoning, provider, model,
                MessageStatus.ERROR, code, message, createdAt, now);
    }

    public boolean isUser() {
        return role == MessageRole.USER;
    }

    public boolean isAssistant() {
        return role == MessageRole.ASSISTANT;
    }

    /**
     * Whether the message should be sent to the model as conversation context. Failed and in-flight answers are
     * excluded; cancelled answers are included when they contain text.
     */
    public boolean isContextEligible() {
        return switch (status) {
            case COMPLETE -> !content.isBlank();
            case CANCELLED -> isAssistant() && !content.isBlank();
            case STREAMING, ERROR -> false;
        };
    }
}
