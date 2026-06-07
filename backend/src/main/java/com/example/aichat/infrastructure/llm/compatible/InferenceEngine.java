package com.example.aichat.infrastructure.llm.compatible;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Inference servers that expose an OpenAI-compatible API. The engine only selects defaults for a few
 * engine-specific request parameters (e.g. how to switch off "thinking"); the protocol is identical.
 */
public enum InferenceEngine {
    GENERIC,
    OLLAMA,
    VLLM,
    LLAMA_CPP,
    LM_STUDIO,
    LOCALAI,
    SGLANG;

    public static InferenceEngine from(String value) {
        if (value == null || value.isBlank()) {
            return GENERIC;
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        for (InferenceEngine engine : values()) {
            if (engine.name().replace("_", "").equals(normalized)) {
                return engine;
            }
        }
        throw new IllegalArgumentException("Unknown inference engine '" + value + "'. Supported: "
                + Arrays.stream(values()).map(e -> e.name().toLowerCase(Locale.ROOT).replace('_', '-'))
                .collect(Collectors.joining(", ")));
    }
}
