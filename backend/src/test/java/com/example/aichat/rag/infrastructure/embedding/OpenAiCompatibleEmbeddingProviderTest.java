package com.example.aichat.rag.infrastructure.embedding;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.aichat.rag.application.RagProperties;
import com.example.aichat.rag.domain.EmbeddingException;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class OpenAiCompatibleEmbeddingProviderTest {

    private static final WireMockServer SERVER = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    @BeforeAll
    static void start() {
        SERVER.start();
    }

    @AfterAll
    static void stop() {
        SERVER.stop();
    }

    @BeforeEach
    void reset() {
        SERVER.resetAll();
    }

    private static OpenAiCompatibleEmbeddingProvider provider(int batchSize, int dimensions) {
        RagProperties.Embedding config = new RagProperties.Embedding("openai", "text-embedding-3-small",
                SERVER.baseUrl() + "/v1/", "sk-test", dimensions, batchSize, Duration.ofSeconds(5),
                Duration.ofSeconds(2), 3, 8000, null, null);
        return new OpenAiCompatibleEmbeddingProvider("openai", config, WebClient.builder());
    }

    private static String embeddings(int count, int dims) {
        StringBuilder data = new StringBuilder();
        for (int i = count - 1; i >= 0; i--) {
            data.append(data.isEmpty() ? "" : ",").append("{\"object\":\"embedding\",\"index\":").append(i)
                    .append(",\"embedding\":[");
            for (int d = 0; d < dims; d++) {
                data.append(d == 0 ? "" : ",").append(i + d / 10.0);
            }
            data.append("]}");
        }
        return "{\"object\":\"list\",\"data\":[" + data + "],\"usage\":{\"prompt_tokens\":5,\"total_tokens\":5}}";
    }

    @Test
    void sendsOpenAiRequestsInBatchesAndOrdersResultsByIndex() {
        SERVER.stubFor(post(urlEqualTo("/v1/embeddings"))
                .withRequestBody(equalToJson("{\"input\":[\"a\",\"b\"]}", true, true))
                .willReturn(okJson(embeddings(2, 3))));
        SERVER.stubFor(post(urlEqualTo("/v1/embeddings"))
                .withRequestBody(equalToJson("{\"input\":[\"c\"]}", true, true))
                .willReturn(okJson(embeddings(1, 3))));

        List<float[]> vectors = provider(2, 0).embed(List.of("a", "b", "c"));

        assertThat(vectors).hasSize(3);
        assertThat(vectors.get(0)[0]).isEqualTo(0f);
        assertThat(vectors.get(1)[0]).isEqualTo(1f);
        SERVER.verify(2, postRequestedFor(urlEqualTo("/v1/embeddings"))
                .withHeader("Authorization", equalTo("Bearer sk-test"))
                .withRequestBody(equalToJson("{\"model\":\"text-embedding-3-small\",\"encoding_format\":\"float\"}",
                        true, true)));
    }

    @Test
    void requestsShortenedVectorsWhenDimensionsAreConfigured() {
        SERVER.stubFor(post(urlEqualTo("/v1/embeddings")).willReturn(okJson(embeddings(1, 4))));

        OpenAiCompatibleEmbeddingProvider provider = provider(8, 4);
        assertThat(provider.embed(List.of("x")).getFirst()).hasSize(4);
        assertThat(provider.dimensions()).isEqualTo(4);
        SERVER.verify(postRequestedFor(urlEqualTo("/v1/embeddings"))
                .withRequestBody(equalToJson("{\"dimensions\":4}", true, true)));
    }

    @Test
    void retriesTransientFailures() {
        SERVER.stubFor(post(urlEqualTo("/v1/embeddings")).inScenario("flaky")
                .whenScenarioStateIs(Scenario.STARTED).willReturn(aResponse().withStatus(503))
                .willSetStateTo("recovered"));
        SERVER.stubFor(post(urlEqualTo("/v1/embeddings")).inScenario("flaky")
                .whenScenarioStateIs("recovered").willReturn(okJson(embeddings(1, 3))));

        assertThat(provider(8, 0).embed(List.of("x"))).hasSize(1);
        SERVER.verify(2, postRequestedFor(urlEqualTo("/v1/embeddings")));
    }

    @Test
    void doesNotRetryAuthenticationFailures() {
        SERVER.stubFor(post(urlEqualTo("/v1/embeddings")).willReturn(aResponse().withStatus(401)
                .withBody("{\"error\":{\"message\":\"Incorrect API key\"}}")));

        assertThatThrownBy(() -> provider(8, 0).embed(List.of("x")))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("rejected the credentials")
                .satisfies(e -> assertThat(((EmbeddingException) e).isRetryable()).isFalse());
        SERVER.verify(1, postRequestedFor(urlEqualTo("/v1/embeddings")));
    }

    @Test
    void reportsUnreachableServersAsRetryable() {
        RagProperties.Embedding config = new RagProperties.Embedding("ollama", "qwen3-embedding:0.6b",
                "http://127.0.0.1:9/v1", null, 0, 8, Duration.ofSeconds(2), Duration.ofSeconds(1), 1, 8000, null, null);

        assertThatThrownBy(() -> new OpenAiCompatibleEmbeddingProvider("ollama", config, WebClient.builder())
                .embed(List.of("x")))
                .isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("unreachable")
                .satisfies(e -> assertThat(((EmbeddingException) e).isRetryable()).isTrue());
    }

    @Test
    void rejectsInconsistentDimensions() {
        SERVER.stubFor(post(urlEqualTo("/v1/embeddings")).willReturn(okJson(embeddings(1, 3))));
        OpenAiCompatibleEmbeddingProvider provider = provider(8, 0);
        provider.embed(List.of("x"));

        SERVER.stubFor(post(urlEqualTo("/v1/embeddings")).willReturn(okJson(embeddings(1, 5))));
        assertThatThrownBy(() -> provider.embed(List.of("y"))).isInstanceOf(EmbeddingException.class)
                .hasMessageContaining("expected 3");
    }
}
