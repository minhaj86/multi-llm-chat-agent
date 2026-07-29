package com.example.aichat.application.chat;

import java.util.UUID;

/**
 * Replace the answer(s) to the last user message with a new answer, optionally from another provider/model.
 */
public record RegenerateCommand(UUID userId,
                                UUID conversationId,
                                String provider,
                                String model,
                                String correlationId) {
}
