package com.example.aichat.api.conversation;

import com.example.aichat.application.conversation.ConversationDetails;
import com.example.aichat.domain.conversation.Conversation;
import com.example.aichat.domain.conversation.Message;
import com.example.aichat.domain.conversation.MessageStatus;
import com.example.aichat.domain.conversation.TitleSource;
import com.example.aichat.domain.llm.ProviderKind;
import com.example.aichat.domain.usage.LlmExecution;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Conversation API bodies.
 */
public final class ConversationDtos {

    private ConversationDtos() {
    }

    public record CreateConversationRequest(@Size(max = 200) String title, UUID systemPromptId) {
    }

    public record RenameConversationRequest(@NotBlank @Size(max = 200) String title) {
    }

    public record SystemPromptSelection(UUID systemPromptId) {
    }

    public record ConversationResponse(UUID id,
                                       String title,
                                       TitleSource titleSource,
                                       UUID systemPromptId,
                                       String lastProvider,
                                       String lastModel,
                                       Instant createdAt,
                                       Instant updatedAt) {

        public static ConversationResponse of(Conversation conversation) {
            return new ConversationResponse(conversation.id(), conversation.title(), conversation.titleSource(),
                    conversation.systemPromptId(), conversation.lastProvider(), conversation.lastModel(),
                    conversation.createdAt(), conversation.updatedAt());
        }
    }

    public record ConversationDetailResponse(ConversationResponse conversation, List<MessageResponse> messages) {

        public static ConversationDetailResponse of(ConversationDetails details) {
            return new ConversationDetailResponse(ConversationResponse.of(details.conversation()),
                    details.messages().stream().map(MessageResponse::of).toList());
        }
    }

    /**
     * A message. {@code role} is lowercase ({@code user}/{@code assistant}); assistant messages carry the provider
     * and model that produced them plus execution metrics.
     */
    public record MessageResponse(UUID id,
                                  UUID conversationId,
                                  String role,
                                  String content,
                                  String reasoning,
                                  String provider,
                                  String model,
                                  MessageStatus status,
                                  String errorCode,
                                  String errorMessage,
                                  Instant createdAt,
                                  ExecutionMetrics metrics) {

        public static MessageResponse of(ConversationDetails.MessageView view) {
            Message message = view.message();
            return new MessageResponse(message.id(), message.conversationId(),
                    message.role().name().toLowerCase(Locale.ROOT), message.content(), message.reasoning(),
                    message.provider(), message.model(), message.status(), message.errorCode(),
                    message.errorMessage(), message.createdAt(), ExecutionMetrics.of(view.execution()));
        }
    }

    public record ExecutionMetrics(long latencyMs,
                                   Long timeToFirstTokenMs,
                                   Integer inputTokens,
                                   Integer outputTokens,
                                   BigDecimal estimatedCost,
                                   boolean local) {

        static ExecutionMetrics of(LlmExecution execution) {
            if (execution == null) {
                return null;
            }
            return new ExecutionMetrics(execution.latencyMs(), execution.timeToFirstTokenMs(),
                    execution.inputTokens(), execution.outputTokens(), execution.estimatedCost(),
                    execution.providerKind() == ProviderKind.LOCAL);
        }
    }
}
