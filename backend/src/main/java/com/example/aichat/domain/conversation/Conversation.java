package com.example.aichat.domain.conversation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A chat conversation owned by a single user. A conversation is not bound to a provider: every assistant
 * message records the provider/model that produced it; {@code lastProvider}/{@code lastModel} only remember
 * the most recent selection for the UI.
 *
 * @param summary         rolling summary of older messages (SUMMARY_PLUS_RECENT context strategy)
 * @param summarizedUntil creation time of the newest message covered by {@code summary}
 */
public record Conversation(UUID id,
                           UUID userId,
                           String title,
                           TitleSource titleSource,
                           UUID systemPromptId,
                           String lastProvider,
                           String lastModel,
                           String summary,
                           Instant summarizedUntil,
                           Instant createdAt,
                           Instant updatedAt) {

    public static final int MAX_TITLE_LENGTH = 200;

    public Conversation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        title = normalizeTitle(title);
        titleSource = titleSource == null ? TitleSource.DEFAULT : titleSource;
    }

    public static Conversation start(UUID userId, String provisionalTitle, UUID systemPromptId,
                                     String provider, String model, Instant now) {
        return new Conversation(UUID.randomUUID(), userId, provisionalTitle, TitleSource.DEFAULT, systemPromptId,
                provider, model, null, null, now, now);
    }

    public boolean isOwnedBy(UUID candidateUserId) {
        return userId.equals(candidateUserId);
    }

    public Conversation rename(String newTitle, Instant now) {
        return new Conversation(id, userId, newTitle, TitleSource.USER, systemPromptId, lastProvider, lastModel,
                summary, summarizedUntil, createdAt, now);
    }

    public Conversation withSystemPrompt(UUID promptId, Instant now) {
        return new Conversation(id, userId, title, titleSource, promptId, lastProvider, lastModel,
                summary, summarizedUntil, createdAt, now);
    }

    public boolean hasSummary() {
        return summary != null && !summary.isBlank() && summarizedUntil != null;
    }

    static String normalizeTitle(String raw) {
        if (raw == null || raw.isBlank()) {
            return "New chat";
        }
        String singleLine = raw.strip().replaceAll("\\s+", " ");
        return singleLine.length() <= MAX_TITLE_LENGTH ? singleLine : singleLine.substring(0, MAX_TITLE_LENGTH);
    }
}
