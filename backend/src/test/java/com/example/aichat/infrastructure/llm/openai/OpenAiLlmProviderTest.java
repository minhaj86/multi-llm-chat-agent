package com.example.aichat.infrastructure.llm.openai;

import static com.example.aichat.support.LlmTestSupport.adapterSupport;
import static com.example.aichat.support.LlmTestSupport.completion;
import static com.example.aichat.support.LlmTestSupport.namedSse;
import static com.example.aichat.support.LlmTestSupport.reasoning;
import static com.example.aichat.support.LlmTestSupport.request;
import static com.example.aichat.support.LlmTestSupport.text;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.aichat.domain.llm.FinishReason;
import com.example.aichat.domain.llm.HealthStatus;
import com.example.aichat.domain.llm.LlmChunk;
import com.example.aichat.domain.llm.LlmModel;
import com.example.aichat.domain.llm.LlmRequest;
import com.example.aichat.domain.llm.LlmResponse;
import com.example.aichat.domain.llm.ProviderKind;
import com.example.aichat.domain.llm.ReasoningMode;
import com.example.aichat.domain.llm.exception.LlmAuthenticationException;
import com.example.aichat.domain.llm.exception.LlmRateLimitException;
import com.example.aichat.domain.llm.exception.LlmResponseException;
import com.example.aichat.infrastructure.llm.ProviderProperties;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** OpenAI Responses API adapter - all calls go to WireMock, never to api.openai.com. */
class OpenAiLlmProviderTest {

    @RegisterExtension
    static WireMockExtension server = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private static ProviderProperties properties(String apiKey) {
        ProviderProperties properties = new ProviderProperties();
        properties.setType("openai");
        properties.setBaseUrl(server.baseUrl());
        properties.setApiKey(apiKey);
        properties.setDefaultModel("gpt-5-mini");
        properties.setTimeout(Duration.ofSeconds(5));
        properties.setHealthTimeout(Duration.ofSeconds(2));
        return properties;
    }

    private static OpenAiLlmProvider provider() {
        return new OpenAiLlmProvider("openai", properties("sk-test-1234567890abcdef"), adapterSupport());
    }

    @Test
    void describesACloudProviderAndMasksTheKey() {
        OpenAiLlmProvider provider = provider();

        assertThat(provider.descriptor().kind()).isEqualTo(ProviderKind.CLOUD);
        assertThat(provider.descriptor().endpoint()).isEqualTo(server.baseUrl() + "/v1");
        assertThat(provider.descriptor().maskedApiKey()).isEqualTo("sk-••••cdef").doesNotContain("1234567890");
    }

    @Test
    void sendsResponsesApiRequestAndParsesTheResponse() {
        server.stubFor(post(urlEqualTo("/v1/responses")).willReturn(okJson("""
                {"id":"resp_1","object":"response","status":"completed","model":"gpt-5-mini-2025-08-07",
                 "output":[
                   {"type":"reasoning","summary":[{"type":"summary_text","text":"Considered options"}]},
                   {"type":"message","role":"assistant","content":[{"type":"output_text","text":"Hello "},
                                                                     {"type":"output_text","text":"there"}]}],
                 "usage":{"input_tokens":21,"output_tokens":9,"output_tokens_details":{"reasoning_tokens":4}}}""")));
        LlmRequest base = request("gpt-5-mini");

        LlmResponse response = provider().chat(base.withOptions(base.options()
                .withTemperature(0.7).withMaxOutputTokens(256).withReasoning(ReasoningMode.DISABLED))).block();

        assertThat(response.content()).isEqualTo("Hello there");
        assertThat(response.reasoning()).isEqualTo("Considered options");
        assertThat(response.usage().inputTokens()).isEqualTo(21);
        assertThat(response.usage().reasoningTokens()).isEqualTo(4);
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
        server.verify(postRequestedFor(urlEqualTo("/v1/responses"))
                .withHeader("Authorization", equalTo("Bearer sk-test-1234567890abcdef"))
                .withHeader("X-Client-Request-Id", equalTo("test-correlation-id"))
                .withRequestBody(matchingJsonPath("$.instructions", equalTo("You are helpful.")))
                .withRequestBody(matchingJsonPath("$.input[0].role", equalTo("user")))
                .withRequestBody(matchingJsonPath("$.input[0].content", equalTo("Hello?")))
                .withRequestBody(matchingJsonPath("$.store", equalTo("false")))
                .withRequestBody(matchingJsonPath("$.max_output_tokens", equalTo("256")))
                .withRequestBody(matchingJsonPath("$.reasoning.effort", equalTo("low")))
                .withRequestBody(notContaining("temperature")));
    }

    @Test
    void nonReasoningModelsReceiveTemperature() {
        server.stubFor(post(urlEqualTo("/v1/responses")).willReturn(okJson(
                "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"ok\"}]}]}")));
        LlmRequest base = request("gpt-4o");

        provider().chat(base.withOptions(base.options().withTemperature(0.4))).block();

        server.verify(postRequestedFor(urlEqualTo("/v1/responses"))
                .withRequestBody(matchingJsonPath("$.temperature", equalTo("0.4")))
                .withRequestBody(notContaining("reasoning")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"gpt-6-luna", "gpt-6-sol", "gpt-6-astra", "gpt-6.1-sol", "gpt-6.1-terra"})
    void gpt6BackgroundRequestsUseLowReasoningWithoutTemperature(String model) {
        server.stubFor(post(urlEqualTo("/v1/responses")).willReturn(okJson("""
                {"status":"completed","output":[{"type":"message",
                  "content":[{"type":"output_text","text":"A useful title"}]}]}""")));
        LlmRequest base = request(model);

        LlmResponse response = provider().chat(base.withOptions(base.options()
                .withTemperature(0.3).withMaxOutputTokens(512).withReasoning(ReasoningMode.DISABLED))).block();

        assertThat(response.content()).isEqualTo("A useful title");
        server.verify(postRequestedFor(urlEqualTo("/v1/responses"))
                .withRequestBody(matchingJsonPath("$.model", equalTo(model)))
                .withRequestBody(matchingJsonPath("$.max_output_tokens", equalTo("512")))
                .withRequestBody(matchingJsonPath("$.reasoning.effort", equalTo("low")))
                .withRequestBody(notContaining("temperature")));
    }

    @Test
    void streamsTypedResponseEvents() {
        server.stubFor(post(urlEqualTo("/v1/responses")).willReturn(aResponse()
                .withHeader("Content-Type", "text/event-stream")
                .withBody(namedSse(
                        "response.created", "{\"type\":\"response.created\",\"response\":{\"id\":\"resp_9\",\"model\":\"gpt-5-mini\"}}",
                        "response.reasoning_summary_text.delta", "{\"type\":\"response.reasoning_summary_text.delta\",\"delta\":\"Plan\"}",
                        "response.output_text.delta", "{\"type\":\"response.output_text.delta\",\"delta\":\"Hel\"}",
                        "response.output_text.delta", "{\"type\":\"response.output_text.delta\",\"delta\":\"lo\"}",
                        "response.completed", "{\"type\":\"response.completed\",\"response\":{\"id\":\"resp_9\",\"status\":\"completed\",\"model\":\"gpt-5-mini\",\"usage\":{\"input_tokens\":5,\"output_tokens\":2}}}"))));

        List<LlmChunk> chunks = provider().stream(request("gpt-5-mini")).collectList().block();

        assertThat(text(chunks)).isEqualTo("Hello");
        assertThat(reasoning(chunks)).isEqualTo("Plan");
        LlmChunk.Completed done = completion(chunks);
        assertThat(done.responseId()).isEqualTo("resp_9");
        assertThat(done.usage().outputTokens()).isEqualTo(2);
        server.verify(postRequestedFor(urlEqualTo("/v1/responses")).withRequestBody(matchingJsonPath("$.stream", equalTo("true"))));
    }

    @Test
    void incompleteResponsesReportTheTokenLimit() {
        server.stubFor(post(urlEqualTo("/v1/responses")).willReturn(aResponse()
                .withHeader("Content-Type", "text/event-stream")
                .withBody(namedSse(
                        "response.output_text.delta", "{\"type\":\"response.output_text.delta\",\"delta\":\"Long\"}",
                        "response.incomplete", "{\"type\":\"response.incomplete\",\"response\":{\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"max_output_tokens\"}}}"))));

        assertThat(completion(provider().stream(request("gpt-5-mini")).collectList().block()).finishReason())
                .isEqualTo(FinishReason.LENGTH);
    }

    @Test
    void failedResponseEventBecomesAnError() {
        server.stubFor(post(urlEqualTo("/v1/responses")).willReturn(aResponse()
                .withHeader("Content-Type", "text/event-stream")
                .withBody(namedSse("response.failed",
                        "{\"type\":\"response.failed\",\"response\":{\"status\":\"failed\",\"error\":{\"code\":\"server_error\",\"message\":\"Something broke\"}}}"))));

        assertThatThrownBy(() -> provider().stream(request("gpt-5-mini")).blockLast())
                .isInstanceOf(LlmResponseException.class)
                .hasMessageContaining("Something broke");
    }

    @Test
    void missingApiKeyFailsWithoutCallingOpenAi() {
        OpenAiLlmProvider provider = new OpenAiLlmProvider("openai", properties(""), adapterSupport());

        assertThatThrownBy(() -> provider.stream(request("gpt-5-mini")).blockLast())
                .isInstanceOf(LlmAuthenticationException.class)
                .hasMessageContaining("not configured");
        assertThat(provider.health().status()).isEqualTo(HealthStatus.DOWN);
        assertThat(provider.health().message()).contains("OPENAI_API_KEY");
        assertThat(server.getAllServeEvents()).isEmpty();
    }

    @Test
    void invalidKeyAndQuotaErrorsAreMapped() {
        server.stubFor(post(urlEqualTo("/v1/responses")).willReturn(aResponse().withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":{\"message\":\"Incorrect API key provided: sk-tes***cdef\",\"type\":\"invalid_request_error\",\"code\":\"invalid_api_key\"}}")));
        assertThatThrownBy(() -> provider().chat(request("gpt-5-mini")).block())
                .isInstanceOf(LlmAuthenticationException.class)
                .hasMessageNotContaining("sk-");

        server.stubFor(post(urlEqualTo("/v1/responses")).willReturn(aResponse().withStatus(429)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":{\"message\":\"You exceeded your current quota\",\"type\":\"insufficient_quota\",\"code\":\"insufficient_quota\"}}")));
        assertThatThrownBy(() -> provider().chat(request("gpt-5-mini")).block())
                .isInstanceOfSatisfying(LlmRateLimitException.class, e -> {
                    assertThat(e.isQuotaExceeded()).isTrue();
                    assertThat(e.isRetryable()).isFalse();
                });
    }

    @Test
    void discoversOnlyChatModels() {
        server.stubFor(get(urlEqualTo("/v1/models")).willReturn(okJson("""
                {"data":[{"id":"gpt-5-mini","owned_by":"system"},{"id":"text-embedding-3-small"},
                         {"id":"gpt-4o-mini-tts"},{"id":"o3"},{"id":"dall-e-3"},{"id":"whisper-1"},{"id":"gpt-4o"}]}""")));

        assertThat(provider().availableModels()).extracting(LlmModel::id).containsExactly("gpt-4o", "gpt-5-mini", "o3");
        assertThat(provider().health().status()).isEqualTo(HealthStatus.UP);
    }

    @Test
    void acceptsBaseUrlWithOrWithoutVersionSuffix() {
        assertThat(OpenAiLlmProvider.apiBaseUrl("https://api.openai.com")).isEqualTo("https://api.openai.com/v1");
        assertThat(OpenAiLlmProvider.apiBaseUrl("https://api.openai.com/v1/")).isEqualTo("https://api.openai.com/v1");
    }
}
