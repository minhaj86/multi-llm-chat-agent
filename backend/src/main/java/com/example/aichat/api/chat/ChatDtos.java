package com.example.aichat.api.chat;

import com.example.aichat.application.chat.ChatEvent;
import com.example.aichat.application.chat.ChatResult;
import com.example.aichat.domain.conversation.MessageStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Chat API bodies.
 */
public final class ChatDtos {

    private ChatDtos() {
    }

    /**
     * @param conversationId existing conversation or {@code null} to start a new one
     * @param provider       provider id from {@code GET /api/providers} ({@code null} = default)
     * @param model          model id from {@code GET /api/providers/{id}/models} ({@code null} = provider default)
     */
    public record ChatRequest(UUID conversationId,
                              @NotBlank @Size(max = 200_000) String message,
                              @Size(max = 64) String provider,
                              @Size(max = 200) String model,
                              UUID systemPromptId) {

        @Override
        public String toString() {
            return "ChatRequest{conversationId=" + conversationId + ", provider=" + provider + ", model=" + model
                    + ", messageLength=" + (message == null ? 0 : message.length()) + "}";
        }
    }

    public record RegenerateRequest(@NotNull UUID conversationId,
                                    @Size(max = 64) String provider,
                                    @Size(max = 200) String model) {
    }

    public record TextPayload(String text) {
    }

    public record CancelResponse(boolean cancelled) {
    }

    public record ChatResponse(UUID conversationId,
                               UUID userMessageId,
                               UUID assistantMessageId,
                               String provider,
                               String model,
                               String content,
                               String reasoning,
                               MessageStatus status,
                               ChatEvent.Completed metrics,
                               String conversationTitle) {

        public static ChatResponse of(ChatResult result) {
            ChatEvent.Started started = result.started();
            ChatEvent.Completed completed = result.completed();
            String title = result.titles().isEmpty() ? started.conversationTitle()
                    : result.titles().getLast().title();
            return new ChatResponse(started.conversationId(), started.userMessageId(), started.assistantMessageId(),
                    started.provider(), started.model(), result.content(), result.reasoning(),
                    completed == null ? MessageStatus.ERROR : completed.status(), completed, title);
        }
    }
}
