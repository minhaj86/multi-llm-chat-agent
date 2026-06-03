package com.example.aichat.infrastructure.llm;

import com.example.aichat.domain.llm.LlmProvider;

/**
 * Service provider interface for adapters. To support a new vendor (Anthropic, Gemini, Bedrock, ...) implement an
 * {@link LlmProvider} adapter plus one factory bean; configure {@code llm.providers.<id>.type} accordingly. No chat
 * or registry code has to change.
 */
public interface LlmProviderFactory {

    /** Adapter type handled by this factory (matched against {@code llm.providers.<id>.type}). */
    String type();

    /**
     * Optional model family (matched against {@code llm.providers.<id>.family}); {@code null} means this factory is
     * the generic fallback for its type.
     */
    default String family() {
        return null;
    }

    LlmProvider create(String providerId, ProviderProperties properties);
}
