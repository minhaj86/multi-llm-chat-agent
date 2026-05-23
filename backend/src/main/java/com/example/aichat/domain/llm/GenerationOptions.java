package com.example.aichat.domain.llm;

/**
 * Sampling / generation options. {@code null} values mean "use the provider default".
 */
public record GenerationOptions(Double temperature, Integer maxOutputTokens, ReasoningMode reasoning) {

    public static final GenerationOptions DEFAULTS = new GenerationOptions(null, null, ReasoningMode.DEFAULT);

    public GenerationOptions {
        if (temperature != null && (temperature < 0.0 || temperature > 2.0)) {
            throw new IllegalArgumentException("temperature must be between 0.0 and 2.0");
        }
        if (maxOutputTokens != null && maxOutputTokens < 1) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
        reasoning = reasoning == null ? ReasoningMode.DEFAULT : reasoning;
    }

    public GenerationOptions withReasoning(ReasoningMode mode) {
        return new GenerationOptions(temperature, maxOutputTokens, mode);
    }

    public GenerationOptions withMaxOutputTokens(Integer tokens) {
        return new GenerationOptions(temperature, tokens, reasoning);
    }

    public GenerationOptions withTemperature(Double value) {
        return new GenerationOptions(value, maxOutputTokens, reasoning);
    }
}
