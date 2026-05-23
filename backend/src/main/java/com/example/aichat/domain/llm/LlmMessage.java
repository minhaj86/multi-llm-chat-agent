package com.example.aichat.domain.llm;

import java.util.Objects;

/**
 * A single provider-neutral chat message.
 *
 * <p>Extension point: multimodal input (images, audio, files) can be added as an additional
 * list of content parts without changing existing adapters, which only read {@link #content()}.
 */
public record LlmMessage(LlmRole role, String content) {

    public LlmMessage {
        Objects.requireNonNull(role, "role must not be null");
        content = content == null ? "" : content;
    }

    public static LlmMessage system(String content) {
        return new LlmMessage(LlmRole.SYSTEM, content);
    }

    public static LlmMessage user(String content) {
        return new LlmMessage(LlmRole.USER, content);
    }

    public static LlmMessage assistant(String content) {
        return new LlmMessage(LlmRole.ASSISTANT, content);
    }
}
