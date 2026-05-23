package com.example.aichat.domain.llm;

/**
 * A model offered by a provider.
 *
 * @param contextWindow maximum context size in tokens if known, otherwise {@code null}
 */
public record LlmModel(String id, String displayName, String ownedBy, Integer contextWindow) {

    public LlmModel {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("model id must not be blank");
        }
        displayName = displayName == null || displayName.isBlank() ? id : displayName;
    }

    public static LlmModel of(String id) {
        return new LlmModel(id, id, null, null);
    }
}
