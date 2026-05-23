package com.example.aichat.domain.llm;

/**
 * Token accounting reported by a provider. Values are {@code null} when the provider does not report them.
 */
public record LlmUsage(Integer inputTokens, Integer outputTokens, Integer reasoningTokens) {

    public static final LlmUsage UNKNOWN = new LlmUsage(null, null, null);

    public static LlmUsage of(Integer inputTokens, Integer outputTokens) {
        return new LlmUsage(inputTokens, outputTokens, null);
    }

    public boolean isKnown() {
        return inputTokens != null || outputTokens != null;
    }

    public Integer totalTokens() {
        if (!isKnown()) {
            return null;
        }
        return (inputTokens == null ? 0 : inputTokens) + (outputTokens == null ? 0 : outputTokens);
    }
}
