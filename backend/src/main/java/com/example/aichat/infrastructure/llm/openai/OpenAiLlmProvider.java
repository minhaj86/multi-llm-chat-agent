package com.example.aichat.infrastructure.llm.openai;

import com.example.aichat.common.Secrets;
import com.example.aichat.common.Texts;
import com.example.aichat.domain.llm.FinishReason;
import com.example.aichat.domain.llm.GenerationOptions;
import com.example.aichat.domain.llm.LlmCapability;
import com.example.aichat.domain.llm.LlmChunk;
import com.example.aichat.domain.llm.LlmMessage;
import com.example.aichat.domain.llm.LlmModel;
import com.example.aichat.domain.llm.LlmProvider;
import com.example.aichat.domain.llm.LlmRequest;
import com.example.aichat.domain.llm.LlmResponse;
import com.example.aichat.domain.llm.LlmRole;
import com.example.aichat.domain.llm.LlmUsage;
import com.example.aichat.domain.llm.ProviderDefaults;
import com.example.aichat.domain.llm.ProviderDescriptor;
import com.example.aichat.domain.llm.ProviderHealth;
import com.example.aichat.domain.llm.ProviderKind;
import com.example.aichat.domain.llm.ReasoningMode;
import com.example.aichat.domain.llm.exception.LlmAuthenticationException;
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
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
 * OpenAI adapter based on the Responses API ({@code POST /v1/responses}, streaming via typed SSE events).
 *
 * <p>System messages become {@code instructions}; the conversation becomes {@code input}. Reasoning models
 * (configurable prefixes, e.g. {@code gpt-5}, {@code o3}) do not accept {@code temperature}; they receive a
 * {@code reasoning.effort} instead. Responses are not stored by OpenAI unless {@code store: true} is configured.
 */
public class OpenAiLlmProvider implements LlmProvider {

    public static final String TYPE = "openai";
    static final String RESPONSES_PATH = "/responses";
    static final String MODELS_PATH = "/models";
    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_TYPE =
            new ParameterizedTypeReference<>() {
            };
    private static final Pattern CHAT_MODEL = Pattern.compile("^(gpt-|chatgpt-|o\\d).*");
    private static final List<String> NON_CHAT_MARKERS = List.of("embedding", "tts", "whisper", "dall-e", "image",
            "audio", "realtime", "transcribe", "moderation", "search", "instruct", "computer-use");

    private final String providerId;
    private final ProviderProperties properties;
    private final ProviderDescriptor descriptor;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final LlmErrorTranslator errors;
    private final Clock clock;
    private final Map<String, Object> extraBody;

    public OpenAiLlmProvider(String providerId, ProviderProperties properties, LlmAdapterSupport support) {
        this.providerId = providerId;
        this.properties = properties;
        String baseUrl = apiBaseUrl(Texts.firstNonBlank(properties.getBaseUrl(), "https://api.openai.com"));
        String displayName = Texts.firstNonBlank(properties.getDisplayName(), "OpenAI");
        this.descriptor = new ProviderDescriptor(
                providerId,
                displayName,
                TYPE,
                properties.getKind() != null ? properties.getKind() : ProviderKind.CLOUD,
                EnumSet.of(LlmCapability.STREAMING, LlmCapability.REASONING, LlmCapability.TOOL_CALLING,
                        LlmCapability.VISION, LlmCapability.JSON_OUTPUT),
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
    }

    @Override
    public ProviderDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public Mono<LlmResponse> chat(LlmRequest request) {
        if (!hasApiKey()) {
            return Mono.error(missingKey());
        }
        return Mono.defer(() -> webClient.post()
                        .uri(RESPONSES_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .headers(headers -> commonHeaders(headers, request))
                        .bodyValue(requestBody(request, false))
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, errors::fromResponse)
                        .bodyToMono(JsonNode.class))
                .map(json -> toResponse(json, request))
                .onErrorMap(errors::translate);
    }

    @Override
    public Flux<LlmChunk> stream(LlmRequest request) {
        if (!hasApiKey()) {
            return Flux.error(missingKey());
        }
        return Flux.defer(() -> {
                    StreamState state = new StreamState(request.model());
                    return webClient.post()
                            .uri(RESPONSES_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.TEXT_EVENT_STREAM)
                            .headers(headers -> commonHeaders(headers, request))
                            .bodyValue(requestBody(request, true))
                            .retrieve()
                            .onStatus(HttpStatusCode::isError, errors::fromResponse)
                            .bodyToFlux(SSE_TYPE)
                            .takeWhile(event -> !"[DONE]".equals(Texts.blankToNull(event.data())))
                            .concatMapIterable(state::onEvent)
                            .concatWith(Flux.defer(() -> Flux.just(state.completion())));
                })
                .onErrorMap(errors::translate);
    }

    @Override
    public List<LlmModel> availableModels() {
        if (!properties.getModels().isEmpty()) {
            return properties.getModels().stream().filter(Texts::hasText).map(String::strip).map(LlmModel::of).toList();
        }
        if (!hasApiKey()) {
            return Texts.hasText(properties.getDefaultModel()) ? List.of(LlmModel.of(properties.getDefaultModel()))
                    : List.of();
        }
        return fetchModels().block(properties.getHealthTimeout().multipliedBy(2));
    }

    @Override
    public ProviderHealth health() {
        Instant checkedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        if (!hasApiKey()) {
            return ProviderHealth.down(providerId, "API key not configured (set OPENAI_API_KEY)", null, checkedAt);
        }
        long start = System.nanoTime();
        try {
            fetchModels().block(properties.getHealthTimeout().multipliedBy(2));
            return ProviderHealth.up(providerId, (System.nanoTime() - start) / 1_000_000L, checkedAt);
        } catch (RuntimeException e) {
            Throwable error = errors.translate(Exceptions.unwrap(e));
            return ProviderHealth.down(providerId, error.getMessage(), (System.nanoTime() - start) / 1_000_000L,
                    checkedAt);
        }
    }

    Map<String, Object> requestBody(LlmRequest request, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model());
        String instructions = request.systemMessages().stream()
                .map(LlmMessage::content)
                .filter(Texts::hasText)
                .collect(Collectors.joining("\n\n"));
        if (!instructions.isBlank()) {
            body.put("instructions", instructions);
        }
        List<Map<String, Object>> input = new ArrayList<>();
        for (LlmMessage message : request.nonSystemMessages()) {
            input.add(Map.of("role", message.role() == LlmRole.ASSISTANT ? "assistant" : "user",
                    "content", message.content()));
        }
        body.put("input", input);
        body.put("stream", stream);
        body.put("store", properties.isStore());
        GenerationOptions options = request.options();
        if (options.maxOutputTokens() != null) {
            body.put("max_output_tokens", options.maxOutputTokens());
        }
        boolean reasoningModel = isReasoningModel(request.model());
        if (options.temperature() != null && !reasoningModel) {
            body.put("temperature", options.temperature());
        }
        if (reasoningModel) {
            Map<String, Object> reasoning = new LinkedHashMap<>();
            String effort = options.reasoning() == ReasoningMode.DISABLED
                    ? properties.getReasoningDisabledEffort()
                    : properties.getReasoningEffort();
            if (Texts.hasText(effort)) {
                reasoning.put("effort", effort.strip());
            }
            if (Texts.hasText(properties.getReasoningSummary()) && options.reasoning() != ReasoningMode.DISABLED) {
                reasoning.put("summary", properties.getReasoningSummary().strip());
            }
            if (!reasoning.isEmpty()) {
                body.put("reasoning", reasoning);
            }
        }
        JsonValues.deepMerge(body, extraBody);
        return body;
    }

    boolean isReasoningModel(String model) {
        String lower = model.toLowerCase(Locale.ROOT);
        return properties.getReasoningModelPrefixes().stream()
                .filter(Texts::hasText)
                .anyMatch(prefix -> lower.startsWith(prefix.strip().toLowerCase(Locale.ROOT)));
    }

    private Mono<List<LlmModel>> fetchModels() {
        return webClient.get()
                .uri(MODELS_PATH)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, errors::fromResponse)
                .bodyToMono(JsonNode.class)
                .timeout(properties.getHealthTimeout())
                .map(OpenAiLlmProvider::parseModels)
                .onErrorMap(errors::translate);
    }

    static List<LlmModel> parseModels(JsonNode json) {
        List<LlmModel> models = new ArrayList<>();
        for (JsonNode node : json.path("data")) {
            String id = node.path("id").asText("");
            String lower = id.toLowerCase(Locale.ROOT);
            if (CHAT_MODEL.matcher(lower).matches() && NON_CHAT_MARKERS.stream().noneMatch(lower::contains)) {
                models.add(new LlmModel(id, id, text(node, "owned_by"), null));
            }
        }
        models.sort(Comparator.comparing(LlmModel::id));
        return List.copyOf(models);
    }

    private LlmResponse toResponse(JsonNode json, LlmRequest request) {
        if ("failed".equals(text(json, "status"))) {
            throw errors.fromErrorObject(json.path("error"));
        }
        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        for (JsonNode item : json.path("output")) {
            String type = text(item, "type");
            if ("message".equals(type)) {
                for (JsonNode part : item.path("content")) {
                    String partType = text(part, "type");
                    if ("output_text".equals(partType)) {
                        content.append(part.path("text").asText(""));
                    } else if ("refusal".equals(partType)) {
                        content.append(part.path("refusal").asText(""));
                    }
                }
            } else if ("reasoning".equals(type)) {
                for (JsonNode summary : item.path("summary")) {
                    if (!reasoning.isEmpty()) {
                        reasoning.append("\n\n");
                    }
                    reasoning.append(summary.path("text").asText(""));
                }
            }
        }
        return new LlmResponse(providerId,
                Texts.firstNonBlank(text(json, "model"), request.model()),
                content.toString(),
                reasoning.isEmpty() ? null : reasoning.toString(),
                usage(json.path("usage")),
                finishReason(json),
                text(json, "id"));
    }

    static LlmUsage usage(JsonNode usage) {
        if (usage == null || !usage.isObject()) {
            return LlmUsage.UNKNOWN;
        }
        Integer input = usage.path("input_tokens").isNumber() ? usage.path("input_tokens").asInt() : null;
        Integer output = usage.path("output_tokens").isNumber() ? usage.path("output_tokens").asInt() : null;
        JsonNode reasoning = usage.path("output_tokens_details").path("reasoning_tokens");
        return new LlmUsage(input, output, reasoning.isNumber() ? reasoning.asInt() : null);
    }

    static FinishReason finishReason(JsonNode response) {
        String status = text(response, "status");
        if ("completed".equals(status)) {
            return FinishReason.STOP;
        }
        if ("incomplete".equals(status)) {
            String reason = response.path("incomplete_details").path("reason").asText("");
            return switch (reason) {
                case "max_output_tokens" -> FinishReason.LENGTH;
                case "content_filter" -> FinishReason.CONTENT_FILTER;
                default -> FinishReason.UNKNOWN;
            };
        }
        return FinishReason.UNKNOWN;
    }

    private void commonHeaders(HttpHeaders headers, LlmRequest request) {
        if (Texts.hasText(properties.getOrganization())) {
            headers.set("OpenAI-Organization", properties.getOrganization().strip());
        }
        if (Texts.hasText(properties.getProject())) {
            headers.set("OpenAI-Project", properties.getProject().strip());
        }
        request.correlationId().ifPresent(id -> headers.set("X-Client-Request-Id", id));
    }

    private boolean hasApiKey() {
        return Texts.hasText(properties.getApiKey());
    }

    private LlmAuthenticationException missingKey() {
        return new LlmAuthenticationException(providerId, descriptor.displayName() + " API key is not configured");
    }

    /** Accepts {@code https://api.openai.com} as well as {@code https://api.openai.com/v1}. */
    static String apiBaseUrl(String configured) {
        String url = configured.strip();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url.endsWith("/v1") ? url : url + "/v1";
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    /** Per-stream state of a Responses API event stream. */
    private final class StreamState {

        private String model;
        private String responseId;
        private LlmChunk.Completed completion;

        StreamState(String requestedModel) {
            this.model = requestedModel;
        }

        List<LlmChunk> onEvent(ServerSentEvent<String> event) {
            String data = event.data();
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
            String type = Objects.requireNonNullElse(Texts.firstNonBlank(text(json, "type"), event.event()), "");
            return switch (type) {
                case "response.output_text.delta", "response.refusal.delta" ->
                        List.of(LlmChunk.text(json.path("delta").asText("")));
                case "response.reasoning_summary_text.delta", "response.reasoning_text.delta" ->
                        List.of(LlmChunk.reasoning(json.path("delta").asText("")));
                case "response.created", "response.in_progress" -> {
                    remember(json.path("response"));
                    yield List.of();
                }
                case "response.completed", "response.incomplete" -> {
                    JsonNode response = json.path("response");
                    remember(response);
                    completion = new LlmChunk.Completed(model, usage(response.path("usage")), finishReason(response),
                            responseId);
                    yield List.of();
                }
                case "response.failed" -> throw errors.fromErrorObject(json.path("response").path("error"));
                case "error" -> throw errors.fromErrorObject(json.has("error") ? json.path("error") : json);
                default -> List.of();
            };
        }

        LlmChunk.Completed completion() {
            return completion != null ? completion
                    : new LlmChunk.Completed(model, LlmUsage.UNKNOWN, FinishReason.UNKNOWN, responseId);
        }

        private void remember(JsonNode response) {
            model = Texts.firstNonBlank(text(response, "model"), model);
            responseId = Texts.firstNonBlank(text(response, "id"), responseId);
        }
    }
}
