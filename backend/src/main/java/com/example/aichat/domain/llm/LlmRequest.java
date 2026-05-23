package com.example.aichat.domain.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Provider-neutral LLM request. Adapters translate it into vendor specific payloads.
 *
 * @param model    concrete model identifier understood by the target provider
 * @param messages ordered conversation including system messages
 * @param options  sampling options
 * @param timeout  maximum time to wait for a complete response (or between streamed chunks);
 *                 {@code null} means "adapter default"
 * @param metadata non-sensitive request metadata (e.g. correlation id) that adapters may forward
 */
public record LlmRequest(String model,
                         List<LlmMessage> messages,
                         GenerationOptions options,
                         Duration timeout,
                         Map<String, String> metadata) {

    public static final String CORRELATION_ID = "correlationId";

    public LlmRequest {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        messages = List.copyOf(messages);
        options = options == null ? GenerationOptions.DEFAULTS : options;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        if (timeout != null && (timeout.isNegative() || timeout.isZero())) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    public Optional<Duration> timeoutValue() {
        return Optional.ofNullable(timeout);
    }

    public Optional<String> correlationId() {
        return Optional.ofNullable(metadata.get(CORRELATION_ID));
    }

    public List<LlmMessage> systemMessages() {
        return messages.stream().filter(m -> m.role() == LlmRole.SYSTEM).toList();
    }

    public List<LlmMessage> nonSystemMessages() {
        return messages.stream().filter(m -> m.role() != LlmRole.SYSTEM).toList();
    }

    public LlmRequest withModel(String newModel) {
        return new LlmRequest(newModel, messages, options, timeout, metadata);
    }

    public LlmRequest withOptions(GenerationOptions newOptions) {
        return new LlmRequest(model, messages, newOptions, timeout, metadata);
    }

    public static Builder builder(String model) {
        return new Builder(model);
    }

    public static final class Builder {
        private final String model;
        private final List<LlmMessage> messages = new ArrayList<>();
        private GenerationOptions options = GenerationOptions.DEFAULTS;
        private Duration timeout;
        private final Map<String, String> metadata = new LinkedHashMap<>();

        private Builder(String model) {
            this.model = model;
        }

        public Builder message(LlmMessage message) {
            messages.add(message);
            return this;
        }

        public Builder messages(List<LlmMessage> values) {
            messages.addAll(values);
            return this;
        }

        public Builder system(String content) {
            return message(LlmMessage.system(content));
        }

        public Builder user(String content) {
            return message(LlmMessage.user(content));
        }

        public Builder assistant(String content) {
            return message(LlmMessage.assistant(content));
        }

        public Builder options(GenerationOptions value) {
            this.options = value;
            return this;
        }

        public Builder timeout(Duration value) {
            this.timeout = value;
            return this;
        }

        public Builder metadata(String key, String value) {
            if (key != null && value != null) {
                metadata.put(key, value);
            }
            return this;
        }

        public LlmRequest build() {
            return new LlmRequest(model, messages, options, timeout, metadata);
        }
    }
}
