package com.example.aichat.domain.llm;

/**
 * Provider-neutral hint about model "thinking". Adapters translate it into whatever the vendor or
 * inference engine understands (reasoning effort, chat-template switches, ...) or ignore it.
 */
public enum ReasoningMode {
    /** Use the model's / provider's default behaviour. */
    DEFAULT,
    /** Ask for a fast answer without extended reasoning (used for titles and summaries). */
    DISABLED
}
