package com.example.aichat.rag.infrastructure.embedding;

import com.example.aichat.rag.application.RagProperties;
import com.example.aichat.rag.domain.EmbeddingException;
import com.example.aichat.rag.domain.EmbeddingProvider;
import com.fasterxml.jackson.databind.JsonNode;
import io.netty.channel.ChannelOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

/**
 * Embeddings through the OpenAI embeddings API ({@code POST /embeddings}), which is also served by Ollama, vLLM,
 * LM Studio, LocalAI and llama.cpp ({@code --embeddings}). Inputs are sent in batches; transient failures
 * (unreachable server, 429, 5xx, timeouts) are retried with backoff because embedding requests have no side
 * effects. Input text is never logged.
 */
public class OpenAiCompatibleEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleEmbeddingProvider.class);
    private static final int MAX_RESPONSE_BYTES = 64 * 1024 * 1024;
    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(500);

    private final String providerId;
    private final RagProperties.Embedding config;
    private final WebClient webClient;
    private final AtomicInteger dimensions;

    public OpenAiCompatibleEmbeddingProvider(String providerId, RagProperties.Embedding config,
                                             WebClient.Builder builder) {
        this.providerId = providerId;
        this.config = config;
        this.dimensions = new AtomicInteger(Math.max(0, config.dimensions()));
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) config.connectTimeout().toMillis())
                .responseTimeout(config.timeout());
        this.webClient = builder.clone()
                .baseUrl(trimTrailingSlash(config.baseUrl()))
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_RESPONSE_BYTES))
                .defaultHeaders(headers -> {
                    if (config.apiKey() != null && !config.apiKey().isBlank()) {
                        headers.setBearerAuth(config.apiKey().strip());
                    }
                })
                .build();
    }

    @Override
    public String providerId() {
        return providerId;
    }

    @Override
    public String modelId() {
        return config.model();
    }

    @Override
    public int dimensions() {
        return dimensions.get();
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> result = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += config.batchSize()) {
            List<String> batch = texts.subList(from, Math.min(texts.size(), from + config.batchSize())).stream()
                    .map(this::limit).toList();
            result.addAll(embedWithRetry(batch));
        }
        return result;
    }

    private List<float[]> embedWithRetry(List<String> batch) {
        for (int attempt = 1; ; attempt++) {
            try {
                return embedBatch(batch);
            } catch (EmbeddingException e) {
                if (!e.isRetryable() || attempt >= config.maxAttempts()) {
                    throw e;
                }
                Duration backoff = INITIAL_BACKOFF.multipliedBy(1L << (attempt - 1));
                log.warn("Embedding request to {} failed (attempt {}/{}), retrying in {} ms: {}", providerId, attempt,
                        config.maxAttempts(), backoff.toMillis(), e.getMessage());
                sleep(backoff);
            }
        }
    }

    private List<float[]> embedBatch(List<String> batch) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        body.put("input", batch);
        body.put("encoding_format", "float");
        if (config.dimensions() > 0) {
            body.put("dimensions", config.dimensions());
        }
        JsonNode response;
        try {
            response = webClient.post()
                    .uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r -> r.bodyToMono(String.class).defaultIfEmpty("")
                            .flatMap(text -> Mono.error(httpError(r.statusCode().value(), text))))
                    .bodyToMono(JsonNode.class)
                    .block(config.timeout().plusSeconds(5));
        } catch (EmbeddingException e) {
            throw e;
        } catch (WebClientRequestException e) {
            throw new EmbeddingException("Embedding service " + providerId + " is unreachable at "
                    + config.baseUrl(), true, e);
        } catch (IllegalStateException e) {
            throw new EmbeddingException("Embedding service " + providerId + " timed out", true, e);
        } catch (RuntimeException e) {
            Throwable cause = e.getCause() instanceof EmbeddingException embedding ? embedding : e;
            if (cause instanceof EmbeddingException embedding) {
                throw embedding;
            }
            throw new EmbeddingException("Embedding request to " + providerId + " failed: " + e.getMessage(), true, e);
        }
        return parse(response, batch.size());
    }

    private List<float[]> parse(JsonNode response, int expected) {
        JsonNode data = response == null ? null : response.path("data");
        if (data == null || !data.isArray() || data.size() != expected) {
            throw new EmbeddingException("Embedding service " + providerId + " returned "
                    + (data == null || !data.isArray() ? "no embeddings" : data.size() + " embeddings for "
                    + expected + " inputs"), false, null);
        }
        float[][] vectors = new float[expected][];
        for (int i = 0; i < data.size(); i++) {
            JsonNode item = data.get(i);
            int index = item.path("index").asInt(i);
            JsonNode values = item.path("embedding");
            if (index < 0 || index >= expected || !values.isArray() || values.isEmpty()) {
                throw new EmbeddingException("Embedding service " + providerId + " returned a malformed embedding",
                        false, null);
            }
            float[] vector = new float[values.size()];
            for (int j = 0; j < vector.length; j++) {
                vector[j] = (float) values.get(j).asDouble();
            }
            vectors[index] = vector;
        }
        for (float[] vector : vectors) {
            if (vector == null) {
                throw new EmbeddingException("Embedding service " + providerId + " returned incomplete results",
                        false, null);
            }
            int known = dimensions.get();
            if (known == 0) {
                dimensions.compareAndSet(0, vector.length);
            } else if (known != vector.length) {
                throw new EmbeddingException("Embedding service " + providerId + " returned " + vector.length
                        + " dimensions, expected " + known, false, null);
            }
        }
        return List.of(vectors);
    }

    private EmbeddingException httpError(int status, String body) {
        String detail = errorDetail(body);
        return switch (status) {
            case 401, 403 -> new EmbeddingException("Embedding service " + providerId
                    + " rejected the credentials (HTTP " + status + ")", false, null);
            case 404 -> new EmbeddingException("Embedding model '" + config.model() + "' or endpoint not found at "
                    + config.baseUrl() + detail, false, null);
            case 408, 429, 500, 502, 503, 504 -> new EmbeddingException("Embedding service " + providerId
                    + " is temporarily unavailable (HTTP " + status + ")" + detail, true, null);
            default -> new EmbeddingException("Embedding service " + providerId + " rejected the request (HTTP "
                    + status + ")" + detail, status >= 500, null);
        };
    }

    private static String errorDetail(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String compact = body.replaceAll("\\s+", " ").strip();
        return ": " + (compact.length() > 300 ? compact.substring(0, 300) + "..." : compact);
    }

    private String limit(String text) {
        return text.length() <= config.maxInputCharacters() ? text : text.substring(0, config.maxInputCharacters());
    }

    private static String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmbeddingException("Interrupted while waiting to retry the embedding request", false, e);
        }
    }
}
