package com.example.aichat.application.chat;

import com.example.aichat.application.chat.knowledge.KnowledgeCitation;
import com.example.aichat.application.provider.ProviderRef;
import com.example.aichat.domain.conversation.MessageStatus;
import com.example.aichat.domain.llm.FinishReason;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Provider-neutral events of a chat generation, streamed to the client as Server-Sent Events.
 */
public sealed interface ChatEvent {

    /** Generation accepted; ids of the persisted user message and the assistant message being generated. */
    record Started(UUID conversationId,
                   UUID userMessageId,
                   UUID assistantMessageId,
                   String provider,
                   String providerName,
                   String model,
                   boolean newConversation,
                   String conversationTitle,
                   boolean regenerated) implements ChatEvent {
    }

    record Delta(String text) implements ChatEvent {
    }

    record Reasoning(String text) implements ChatEvent {
    }

    /** Generation finished (status {@code COMPLETE} or {@code CANCELLED}) with usage metrics. */
    record Completed(UUID conversationId,
                     UUID assistantMessageId,
                     MessageStatus status,
                     String provider,
                     String model,
                     FinishReason finishReason,
                     Integer inputTokens,
                     Integer outputTokens,
                     long latencyMs,
                     Long timeToFirstTokenMs,
                     BigDecimal estimatedCost,
                     boolean local) implements ChatEvent {
    }

    /** Generation failed; {@code alternatives} lists available providers the user can switch to. */
    record Failed(UUID conversationId,
                  UUID assistantMessageId,
                  String code,
                  String message,
                  String provider,
                  String providerName,
                  boolean retryable,
                  List<ProviderRef> alternatives) implements ChatEvent {
    }

    /**
     * Knowledge-base retrieval finished for this answer (sent before the first token). {@code citations} refer to
     * the bracketed numbers the model was asked to use; {@code notice} explains when no knowledge could be used.
     */
    record KnowledgeRetrieved(UUID conversationId,
                              UUID assistantMessageId,
                              List<KnowledgeCitation> citations,
                              int retrievedCount,
                              String notice) implements ChatEvent {
    }

    /** A generated conversation title became available. */
    record TitleUpdated(UUID conversationId, String title) implements ChatEvent {
    }
}
