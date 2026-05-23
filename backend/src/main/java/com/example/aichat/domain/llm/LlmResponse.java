package com.example.aichat.domain.llm;

/**
 * Complete (non-streamed) response of a provider.
 *
 * @param providerId provider that produced the answer
 * @param model      model reported by the provider (may differ from the requested alias)
 * @param content    final answer text
 * @param reasoning  optional reasoning / "thinking" text exposed by the model, {@code null} if none
 */
public record LlmResponse(String providerId,
                          String model,
                          String content,
                          String reasoning,
                          LlmUsage usage,
                          FinishReason finishReason,
                          String responseId) {

    public LlmResponse {
        content = content == null ? "" : content;
        usage = usage == null ? LlmUsage.UNKNOWN : usage;
        finishReason = finishReason == null ? FinishReason.UNKNOWN : finishReason;
    }
}
