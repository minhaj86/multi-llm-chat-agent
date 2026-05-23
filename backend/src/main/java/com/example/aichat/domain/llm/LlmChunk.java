package com.example.aichat.domain.llm;

/**
 * Element of a streamed LLM response.
 *
 * <p>The hierarchy is sealed so that consumers can use exhaustive pattern matching. Future
 * capabilities (tool calls, citations, audio, ...) are added as new record types.
 */
public sealed interface LlmChunk permits LlmChunk.TextDelta, LlmChunk.ReasoningDelta, LlmChunk.Completed {

    /** Incremental answer text. */
    record TextDelta(String text) implements LlmChunk {
        public TextDelta {
            text = text == null ? "" : text;
        }
    }

    /** Incremental reasoning / thinking text (only for models that expose it). */
    record ReasoningDelta(String text) implements LlmChunk {
        public ReasoningDelta {
            text = text == null ? "" : text;
        }
    }

    /** Terminal element carrying usage and finish information. Always the last element of a successful stream. */
    record Completed(String model, LlmUsage usage, FinishReason finishReason, String responseId) implements LlmChunk {
        public Completed {
            usage = usage == null ? LlmUsage.UNKNOWN : usage;
            finishReason = finishReason == null ? FinishReason.UNKNOWN : finishReason;
        }
    }

    static TextDelta text(String text) {
        return new TextDelta(text);
    }

    static ReasoningDelta reasoning(String text) {
        return new ReasoningDelta(text);
    }
}
