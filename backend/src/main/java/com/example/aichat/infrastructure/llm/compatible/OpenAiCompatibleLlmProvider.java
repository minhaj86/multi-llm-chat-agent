package com.example.aichat.infrastructure.llm.compatible;

import com.example.aichat.common.Secrets;
import com.example.aichat.common.Texts;
import com.example.aichat.domain.llm.FinishReason;
import com.example.aichat.domain.llm.GenerationOptions;
import com.example.aichat.domain.llm.LlmChunk;
import com.example.aichat.domain.llm.LlmMessage;
import com.example.aichat.domain.llm.LlmModel;
import com.example.aichat.domain.llm.LlmProvider;
import com.example.aichat.domain.llm.LlmRequest;
import com.example.aichat.domain.llm.LlmResponse;
import com.example.aichat.domain.llm.LlmUsage;
import com.example.aichat.domain.llm.ProviderDefaults;
import com.example.aichat.domain.llm.ProviderDescriptor;
import com.example.aichat.domain.llm.ProviderHealth;
import com.example.aichat.domain.llm.ProviderKind;
import com.example.aichat.domain.llm.ReasoningMode;
import com.example.aichat.domain.llm.exception.LlmErrorCode;
import com.example.aichat.domain.llm.exception.LlmResponseException;
import com.example.aichat.infrastructure.llm.JsonValues;
import com.example.aichat.infrastructure.llm.LlmAdapterSupport;
import com.example.aichat.infrastructure.llm.LlmErrorTranslator;
import com.example.aichat.infrastructure.llm.ProviderProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Adapter for any server implementing the OpenAI Chat Completions API: Ollama, llama.cpp server, vLLM, SGLang,
 * LocalAI, LM Studio, ... Endpoints used: {@code POST {base-url}/chat/completions} and {@code GET {base-url}/models}.
 *
 * <p>Model-family differences (display name, capabilities, reasoning handling) are supplied as a
 * {@link ModelFamilyProfile}; engine differences through {@link InferenceEngine}. Subclasses such as
 * {@code QwenLlmProvider} therefore contain almost no code.
 */
public class OpenAiCompatibleLlmProvider implements LlmProvider {

    public static final String TYPE = "openai-compatible";
    static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    static final String MODELS_PATH = "/models";
    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final Map<String, FinishReason> FINISH_REASONS = Map.of(
            "stop", FinishReason.STOP,
            "eos", FinishReason.STOP,
            "length", FinishReason.LENGTH,
            "content_filter", FinishReason.CONTENT_FILTER,
            "tool_calls", FinishReason.TOOL_CALLS,
            "function_call", FinishReason.TOOL_CALLS);

    protected final String providerId;
    protected final ProviderProperties properties;
    protected final ModelFamilyProfile profile;
    protected final InferenceEngine engine;
    protected final ProviderDescriptor descriptor;
    protected final WebClient webClient;
    protected final ObjectMapper objectMapper;
    protected final LlmErrorTranslator errors;
    protected final Clock clock;
    private final Map<String, Object> extraBody;
    private final Map<String, Object> reasoningDisabledParams;

    public OpenAiCompatibleLlmProvider(String providerId, ProviderProperties properties, ModelFamilyProfile profile,
                                       LlmAdapterSupport support) {
        if (Texts.isBlank(properties.getBaseUrl())) {
            throw new IllegalStateException("llm.providers." + providerId + ".base-url is required");
        }
        this.providerId = providerId;
        this.properties = properties;
        this.profile = profile;
        this.engine = InferenceEngine.from(properties.getEngine());
        String baseUrl = stripTrailingSlash(properties.getBaseUrl().strip());
        String displayName = Texts.firstNonBlank(properties.getDisplayName(), profile.defaultDisplayName(), providerId);
        this.descriptor = new ProviderDescriptor(
                providerId,
                displayName,
                Texts.firstNonBlank(properties.getType(), TYPE),
                properties.getKind() != null ? properties.getKind() : ProviderKind.LOCAL,
                profile.capabilities(),
                baseUrl,
                Secrets.mask(properties.getApiKey()),
                new ProviderDefaults(properties.isEnabled(), Texts.blankToNull(properties.getDefaultModel()),
                        properties.getTimeout(), properties.getTemperature(), properties.getMaxTokens()));
        this.objectMapper = support.objectMapper();
        this.clock = support.clock();
        this.webClient = support.webClientFactory().create(providerId, baseUrl, properties);
        this.errors = new LlmErrorTranslator(providerId, displayName, objectMapper,
                properties.getRetry().getMaxRateLimitWait());
        this.extraBody = JsonValues.coerceMap(properties.getExtraBody());
        this.reasoningDisabledParams = properties.getReasoningDisabledParams().isEmpty()
                ? profile.reasoningDisabledParams(engine)
                : JsonValues.coerceMap(properties.getReasoningDisabledParams());
    }

    @Override
    public ProviderDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public Mono<LlmResponse> chat(LlmRequest request) {
        return Mono.defer(() -> webClient.post()
                        .uri(CHAT_COMPLETIONS_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .headers(headers -> correlationHeader(headers, request))
                        .bodyValue(requestBody(request, false))
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, errors::fromResponse)
                        .bodyToMono(JsonNode.class))
                .map(json -> toResponse(json, request))
                .onErrorMap(errors::translate);
    }

    @Override
    public Flux<LlmChunk> stream(LlmRequest request) {
        return Flux.defer(() -> {
                    StreamState state = new StreamState(request.model(),
                            profile.parseThinkTags() ? new ThinkTagParser() : null);
                    return webClient.post()
                            .uri(CHAT_COMPLETIONS_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.TEXT_EVENT_STREAM)
                            .headers(headers -> correlationHeader(headers, request))
                            .bodyValue(requestBody(request, true))
                            .retrieve()
                            .onStatus(HttpStatusCode::isError, errors::fromResponse)
                            .bodyToFlux(SSE_TYPE)
                            .takeWhile(event -> !"[DONE]".equals(Texts.blankToNull(event.data())))
                            .concatMapIterable(event -> state.onEvent(event.data()))
                            .concatWith(Flux.defer(() -> Flux.fromIterable(state.finish())));
                })
                .onErrorMap(errors::translate);
    }

    @Override
    public List<LlmModel> availableModels() {
        if (!properties.getModels().isEmpty()) {
            return properties.getModels().stream().filter(Texts::hasText).map(String::strip).map(LlmModel::of).toList();
        }
        return fetchModels().block(properties.getHealthTimeout().multipliedBy(2));
    }

    @Override
    public ProviderHealth health() {
        long start = System.nanoTime();
        Instant checkedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        try {
            fetchModels().block(properties.getHealthTimeout().multipliedBy(2));
            return ProviderHealth.up(providerId, (System.nanoTime() - start) / 1_000_000L, checkedAt);
        } catch (RuntimeException e) {
            Throwable error = errors.translate(Exceptions.unwrap(e));
            return ProviderHealth.down(providerId, error.getMessage(), (System.nanoTime() - start) / 1_000_000L,
                    checkedAt);
        }
    }

    protected Mono<List<LlmModel>> fetchModels() {
        return webClient.get()
                .uri(MODELS_PATH)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, errors::fromResponse)
                .bodyToMono(JsonNode.class)
                .timeout(properties.getHealthTimeout())
                .map(this::parseModels)
                .onErrorMap(errors::translate);
    }

    /** Builds the Chat Completions request body. Subclasses may adjust it through {@link #customizeBody}. */
    protected Map<String, Object> requestBody(LlmRequest request, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model());
        List<Map<String, Object>> messages = new ArrayList<>();
        for (LlmMessage message : adaptMessages(request)) {
            messages.add(Map.of("role", message.role().name().toLowerCase(Locale.ROOT), "content", message.content()));
        }
        body.put("messages", messages);
        body.put("stream", stream);
        if (stream && properties.isStreamUsage()) {
            body.put("stream_options", Map.of("include_usage", true));
        }
        GenerationOptions options = request.options();
        if (options.temperature() != null) {
            body.put("temperature", options.temperature());
        }
        if (options.maxOutputTokens() != null) {
            body.put(properties.getMaxTokensParameter(), options.maxOutputTokens());
        }
        JsonValues.deepMerge(body, extraBody);
        if (options.reasoning() == ReasoningMode.DISABLED && !reasoningDisabledParams.isEmpty()) {
            JsonValues.deepMerge(body, reasoningDisabledParams);
        }
        customizeBody(body, request);
        return body;
    }

    /** Hook for model-family specific prompt adjustments. */
    protected List<LlmMessage> adaptMessages(LlmRequest request) {
        return request.messages();
    }

    /** Hook for model-family specific request fields. */
    protected void customizeBody(Map<String, Object> body, LlmRequest request) {
    }

    protected boolean hasReasoningDisabledParams() {
        return !reasoningDisabledParams.isEmpty();
    }

    private LlmResponse toResponse(JsonNode json, LlmRequest request) {
        if (json.has("error") && !json.path("error").isNull()) {
            throw errors.fromErrorObject(json.path("error"));
        }
        JsonNode choice = json.path("choices").path(0);
        if (choice.isMissingNode()) {
            throw new LlmResponseException(providerId, LlmErrorCode.UPSTREAM_ERROR,
                    descriptor.displayName() + " returned no choices");
        }
        JsonNode message = choice.path("message");
        String content = text(message, "content");
        String reasoning = Texts.firstNonBlank(text(message, "reasoning_content"), text(message, "reasoning"));
        if (profile.parseThinkTags() && content != null) {
            ThinkTagParser.Split split = ThinkTagParser.split(content);
            content = split.content();
            reasoning = Texts.firstNonBlank(reasoning, split.reasoning());
        }
        return new LlmResponse(providerId,
                Texts.firstNonBlank(text(json, "model"), request.model()),
                content == null ? "" : content,
                reasoning,
                usage(json.path("usage")),
                finishReason(text(choice, "finish_reason")),
                text(json, "id"));
    }

    private List<LlmModel> parseModels(JsonNode json) {
        List<LlmModel> models = new ArrayList<>();
        JsonNode data = json.has("data") ? json.path("data") : json.path("models");
        for (JsonNode node : data) {
            String id = Texts.firstNonBlank(text(node, "id"), text(node, "name"), text(node, "model"));
            if (Texts.hasText(id)) {
                models.add(new LlmModel(id, id, text(node, "owned_by"), contextWindow(node)));
            }
        }
        models.sort(Comparator.comparing(LlmModel::id, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(models);
    }

    private static Integer contextWindow(JsonNode model) {
        for (String field : new String[] {"context_length", "max_model_len", "context_window"}) {
            if (model.path(field).isNumber()) {
                return model.path(field).asInt();
            }
        }
        return null;
    }

    private void correlationHeader(HttpHeaders headers, LlmRequest request) {
        request.correlationId().ifPresent(id -> headers.set("X-Request-Id", id));
    }

    static LlmUsage usage(JsonNode usage) {
        if (usage == null || !usage.isObject()) {
            return LlmUsage.UNKNOWN;
        }
        Integer input = intOrNull(usage, "prompt_tokens");
        Integer output = intOrNull(usage, "completion_tokens");
        Integer reasoning = usage.path("completion_tokens_details").hasNonNull("reasoning_tokens")
                ? usage.path("completion_tokens_details").path("reasoning_tokens").asInt()
                : null;
        return new LlmUsage(input, output, reasoning);
    }

    static FinishReason finishReason(String value) {
        if (value == null) {
            return FinishReason.UNKNOWN;
        }
        return FINISH_REASONS.getOrDefault(value.toLowerCase(Locale.ROOT), FinishReason.UNKNOWN);
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /** Per-stream parsing state (one instance per subscription). */
    private final class StreamState {

        private final ThinkTagParser thinkParser;
        private String model;
        private String responseId;
        private LlmUsage usage = LlmUsage.UNKNOWN;
        private FinishReason finishReason;

        StreamState(String requestedModel, ThinkTagParser thinkParser) {
            this.model = requestedModel;
            this.thinkParser = thinkParser;
        }

        List<LlmChunk> onEvent(String data) {
            if (Texts.isBlank(data)) {
                return List.of();
            }
            JsonNode json;
            try {
                json = objectMapper.readTree(data);
            } catch (JsonProcessingException e) {
                throw new LlmResponseException(providerId, LlmErrorCode.UPSTREAM_ERROR,
                        descriptor.displayName() + " sent a malformed stream event", null, e);
            }
            if (json.has("error") && !json.path("error").isNull()) {
                JsonNode error = json.path("error");
                throw errors.fromErrorObject(error.isObject() ? error : json);
            }
            model = Texts.firstNonBlank(text(json, "model"), model);
            responseId = Texts.firstNonBlank(responseId, text(json, "id"));
            if (json.path("usage").isObject()) {
                usage = usage(json.path("usage"));
            }
            JsonNode choice = json.path("choices").path(0);
            if (choice.isMissingNode()) {
                return List.of();
            }
            List<LlmChunk> chunks = new ArrayList<>(2);
            JsonNode delta = choice.path("delta");
            String reasoning = Texts.firstNonBlank(text(delta, "reasoning_content"), text(delta, "reasoning"));
            if (reasoning != null && !reasoning.isEmpty()) {
                chunks.add(LlmChunk.reasoning(reasoning));
            }
            String content = text(delta, "content");
            if (content != null && !content.isEmpty()) {
                if (thinkParser != null) {
                    chunks.addAll(thinkParser.accept(content));
                } else {
                    chunks.add(LlmChunk.text(content));
                }
            }
            String finish = text(choice, "finish_reason");
            if (finish != null) {
                finishReason = finishReason(finish);
            }
            return chunks;
        }

        List<LlmChunk> finish() {
            List<LlmChunk> chunks = new ArrayList<>(2);
            if (thinkParser != null) {
                chunks.addAll(thinkParser.flush());
            }
            chunks.add(new LlmChunk.Completed(model, usage, finishReason == null ? FinishReason.STOP : finishReason,
                    responseId));
            return chunks;
        }
    }
}
