package com.example.aichat.infrastructure.llm.llama;

import static com.example.aichat.support.LlmTestSupport.adapterSupport;
import static com.example.aichat.support.LlmTestSupport.compatible;
import static com.example.aichat.support.LlmTestSupport.completion;
import static com.example.aichat.support.LlmTestSupport.request;
import static com.example.aichat.support.LlmTestSupport.sse;
import static com.example.aichat.support.LlmTestSupport.text;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.aichat.domain.llm.HealthStatus;
import com.example.aichat.domain.llm.LlmCapability;
import com.example.aichat.domain.llm.LlmChunk;
import com.example.aichat.domain.llm.LlmModel;
import com.example.aichat.domain.llm.LlmRequest;
import com.example.aichat.domain.llm.ProviderHealth;
import com.example.aichat.domain.llm.ReasoningMode;
import com.example.aichat.domain.llm.exception.LlmErrorCode;
import com.example.aichat.domain.llm.exception.LlmException;
import com.example.aichat.domain.llm.exception.LlmProviderUnavailableException;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class LlamaLlmProviderTest {

    @RegisterExtension
    static WireMockExtension server = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private LlamaLlmProvider provider() {
        return new LlamaLlmProvider("llama", compatible(server.baseUrl() + "/v1", "llama", "llama-cpp", "llama"),
                adapterSupport());
    }

    @Test
    void describesItselfWithoutReasoningCapability() {
        LlamaLlmProvider provider = provider();

        assertThat(provider.descriptor().displayName()).isEqualTo("Llama Local");
        assertThat(provider.descriptor().capabilities()).contains(LlmCapability.STREAMING)
                .doesNotContain(LlmCapability.REASONING);
    }

    @Test
    void streamsAnswer() {
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(aResponse().withHeader("Content-Type", "text/event-stream").withBody(sse(
                        "{\"choices\":[{\"delta\":{\"role\":\"assistant\"}}]}",
                        "{\"choices\":[{\"delta\":{\"content\":\"1, 2\"}}]}",
                        "{\"choices\":[{\"delta\":{\"content\":\", 3\"},\"finish_reason\":\"length\"}],\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":3}}",
                        "[DONE]"))));

        List<LlmChunk> chunks = provider().stream(request("llama")).collectList().block();

        assertThat(text(chunks)).isEqualTo("1, 2, 3");
        assertThat(completion(chunks).finishReason().name()).isEqualTo("LENGTH");
        assertThat(completion(chunks).usage().outputTokens()).isEqualTo(3);
    }

    @Test
    void doesNotSendReasoningParametersToLlama() {
        server.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(okJson(
                "{\"choices\":[{\"message\":{\"content\":\"Hi\"},\"finish_reason\":\"stop\"}]}")));
        LlmRequest request = request("llama");

        provider().chat(request.withOptions(request.options().withReasoning(ReasoningMode.DISABLED))).block();

        server.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(notContaining("reasoning_effort"))
                .withRequestBody(notContaining("chat_template_kwargs"))
                .withRequestBody(notContaining("/no_think")));
    }

    @Test
    void listsModelsAndReportsHealth() {
        server.stubFor(get(urlEqualTo("/v1/models")).willReturn(okJson(
                "{\"object\":\"list\",\"data\":[{\"id\":\"llama\",\"owned_by\":\"llamacpp\"},{\"id\":\"aaa\"}]}")));

        assertThat(provider().availableModels()).extracting(LlmModel::id).containsExactly("aaa", "llama");
        ProviderHealth health = provider().health();
        assertThat(health.status()).isEqualTo(HealthStatus.UP);
        assertThat(health.latencyMs()).isNotNull();
    }

    @Test
    void unknownModelIsReportedAsModelNotFound() {
        server.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":{\"message\":\"model 'nope' not found\",\"type\":\"not_found_error\"}}")));

        assertThatThrownBy(() -> provider().stream(request("nope")).blockLast())
                .isInstanceOfSatisfying(LlmException.class, e -> {
                    assertThat(e.code()).isEqualTo(LlmErrorCode.MODEL_NOT_FOUND);
                    assertThat(e.getMessage()).contains("not found");
                });
    }

    @Test
    void stoppedServerIsReportedAsUnavailableAndRetryable() throws IOException {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        LlamaLlmProvider offline = new LlamaLlmProvider("llama",
                compatible("http://127.0.0.1:" + port + "/v1", "llama", "generic", "llama"), adapterSupport());

        assertThatThrownBy(() -> offline.stream(request("llama")).blockLast())
                .isInstanceOfSatisfying(LlmProviderUnavailableException.class, e -> {
                    assertThat(e.isRetryable()).isTrue();
                    assertThat(e.providerId()).isEqualTo("llama");
                });
        assertThat(offline.health().status()).isEqualTo(HealthStatus.DOWN);
    }
}
