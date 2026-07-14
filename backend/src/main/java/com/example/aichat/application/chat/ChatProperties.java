package com.example.aichat.application.chat;

import com.example.aichat.application.chat.context.ContextStrategy;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Chat behaviour (limits, context management, titles, summaries, compare mode).
 */
@ConfigurationProperties("chat")
public record ChatProperties(@DefaultValue("32000") int maxMessageLength,
                             @DefaultValue("3") int maxConcurrentGenerationsPerUser,
                             @DefaultValue("PT15S") Duration heartbeatInterval,
                             @DefaultValue Context context,
                             @DefaultValue Title title,
                             @DefaultValue Summary summary,
                             @DefaultValue Compare compare) {

    public ChatProperties {
        if (maxMessageLength < 1) {
            throw new IllegalArgumentException("chat.max-message-length must be positive");
        }
        if (maxConcurrentGenerationsPerUser < 1) {
            throw new IllegalArgumentException("chat.max-concurrent-generations-per-user must be positive");
        }
    }

    /**
     * @param maxContextTokens          token budget for system prompt, summary and history (excluding the answer)
     * @param lastNMessages             window size for {@link ContextStrategy#LAST_N_MESSAGES}
     * @param summaryTriggerTokens      unsummarised history size that triggers a new summary
     * @param summaryKeepRecentMessages messages that always stay verbatim (never summarised)
     * @param charsPerToken             heuristic used to estimate tokens
     */
    public record Context(@DefaultValue("SUMMARY_PLUS_RECENT") ContextStrategy strategy,
                          @DefaultValue("8000") int maxContextTokens,
                          @DefaultValue("20") int lastNMessages,
                          @DefaultValue("6000") int summaryTriggerTokens,
                          @DefaultValue("6") int summaryKeepRecentMessages,
                          @DefaultValue("3.5") double charsPerToken) {

        public Context {
            if (maxContextTokens < 256) {
                throw new IllegalArgumentException("chat.context.max-context-tokens must be at least 256");
            }
            if (lastNMessages < 1) {
                throw new IllegalArgumentException("chat.context.last-n-messages must be positive");
            }
            if (summaryKeepRecentMessages < 2) {
                throw new IllegalArgumentException("chat.context.summary-keep-recent-messages must be at least 2");
            }
            if (charsPerToken <= 0) {
                throw new IllegalArgumentException("chat.context.chars-per-token must be positive");
            }
        }
    }

    /**
     * @param mode     {@code LLM} asks a model for a short title, {@code HEURISTIC} uses the first message
     * @param provider optional provider used for titles (defaults to the conversation's provider)
     * @param model    optional model used for titles
     */
    public record Title(@DefaultValue("LLM") TitleMode mode,
                        @DefaultValue("512") int maxTokens,
                        @DefaultValue("PT45S") Duration timeout,
                        String provider,
                        String model) {
    }

    public record Summary(@DefaultValue("true") boolean enabled,
                          @DefaultValue("1024") int maxTokens,
                          @DefaultValue("40") int maxMessagesPerRun,
                          @DefaultValue("PT3M") Duration timeout,
                          String provider,
                          String model) {
    }

    public record Compare(@DefaultValue("4") int maxTargets) {
    }

    public enum TitleMode {
        LLM,
        HEURISTIC
    }
}
