package com.example.aichat.infrastructure.llm.compatible;

import static com.example.aichat.support.LlmTestSupport.adapterSupport;
import static com.example.aichat.support.LlmTestSupport.compatible;
import static com.example.aichat.support.LlmTestSupport.request;
import static com.example.aichat.support.LlmTestSupport.sse;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.aichat.domain.llm.exception.LlmAuthenticationException;
import com.example.aichat.domain.llm.exception.LlmErrorCode;
import com.example.aichat.domain.llm.exception.LlmException;
import com.example.aichat.domain.llm.exception.LlmProviderUnavailableException;
import com.example.aichat.domain.llm.exception.LlmRateLimitException;
import com.example.aichat.infrastructure.llm.ProviderProperties;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Generic OpenAI-compatible behaviour shared by every local engine. */
class OpenAiCompatibleLlmProviderTest {

    @RegisterExtension
    static WireMockExtension server = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private OpenAiCompatibleLlmProvider provider(ProviderProperties properties) {
        return new OpenAiCompatibleLlmProvider("mistral", properties, ModelFamilyProfile.GENERIC, adapterSupport());
    }

    private ProviderProperties properties() {
        ProviderProperties properties = compatible(server.baseUrl() + "/v1/", null, "vllm", "mistral-7b");
        properties.setDisplayName("Mistral Local");
        return properties;
    }

    @Test
    void anyOpenAiCompatibleServerWorksThroughConfigurationOnly() {
        server.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(okJson(
                "{\"model\":\"mistral-7b\",\"choices\":[{\"message\":{\"content\":\"Bonjour\"},\"finish_reason\":\"stop\"}]}")));

        OpenAiCompatibleLlmProvider provider = provider(properties());

        assertThat(provider.descriptor().displayName()).isEqualTo("Mistral Local");
        assertThat(provider.descriptor().endpoint()).isEqualTo(server.baseUrl() + "/v1");
        assertThat(provider.chat(request("mistral-7b")).block().content()).isEqualTo("Bonjour");
    }

    @Test
    void mergesExtraBodyAndCoercesYamlScalars() {
        server.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(okJson(
                "{\"choices\":[{\"message\":{\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}")));
        ProviderProperties properties = properties();
        Map<String, Object> templateKwargs = new LinkedHashMap<>();
        templateKwargs.put("enable_thinking", "false");
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("top_k", "20");
        extra.put("chat_template_kwargs", templateKwargs);
        properties.setExtraBody(extra);
        properties.setMaxTokensParameter("max_completion_tokens");
        properties.setTemperature(0.3);

        provider(properties).chat(request("mistral-7b").withOptions(
                request("mistral-7b").options().withMaxOutputTokens(64).withTemperature(0.3))).block();

        server.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(matchingJsonPath("$[?(@.top_k == 20)]"))
                .withRequestBody(matchingJsonPath("$[?(@.chat_template_kwargs.enable_thinking == false)]"))
                .withRequestBody(matchingJsonPath("$.max_completion_tokens", equalTo("64")))
                .withRequestBody(matchingJsonPath("$.temperature", equalTo("0.3"))));
    }

    @Test
    void mapsVendorErrorsToProviderNeutralExceptions() {
        stubError(401, "{\"error\":{\"message\":\"bad key sk-abc\"}}");
        assertThatThrownBy(() -> provider(properties()).chat(request("m")).block())
                .isInstanceOf(LlmAuthenticationException.class)
                .hasMessageNotContaining("sk-abc");

        stubError(503, "{\"error\":{\"message\":\"overloaded\"}}");
        assertThatThrownBy(() -> provider(properties()).chat(request("m")).block())
                .isInstanceOfSatisfying(LlmProviderUnavailableException.class, e -> assertThat(e.isRetryable()).isTrue());

        stubError(500, "{\"object\":\"error\",\"message\":\"boom\",\"type\":\"InternalServerError\"}");
        assertThatThrownBy(() -> provider(properties()).chat(request("m")).block())
                .isInstanceOfSatisfying(LlmException.class, e -> {
                    assertThat(e.code()).isEqualTo(LlmErrorCode.UPSTREAM_ERROR);
                    assertThat(e.isRetryable()).isFalse();
                });

        stubError(400, "{\"error\":{\"message\":\"This model's maximum context length is 4096 tokens\",\"code\":\"context_length_exceeded\"}}");
        assertThatThrownBy(() -> provider(properties()).chat(request("m")).block())
                .isInstanceOfSatisfying(LlmException.class, e -> {
                    assertThat(e.code()).isEqualTo(LlmErrorCode.INVALID_REQUEST);
                    assertThat(e.getMessage()).contains("maximum context length");
                });
    }

    @Test
    void shortRateLimitsAreRetryableButLongOnesAreNot() {
        server.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(aResponse().withStatus(429)
                .withHeader("Retry-After", "2").withHeader("Content-Type", "application/json")
                .withBody("{\"error\":{\"message\":\"slow down\",\"code\":\"rate_limit_exceeded\"}}")));
        assertThatThrownBy(() -> provider(properties()).chat(request("m")).block())
                .isInstanceOfSatisfying(LlmRateLimitException.class, e -> {
                    assertThat(e.isRetryable()).isTrue();
                    assertThat(e.retryAfter()).contains(Duration.ofSeconds(2));
                });

        server.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(aResponse().withStatus(429)
                .withHeader("Retry-After", "60").withHeader("Content-Type", "application/json")
                .withBody("{\"error\":{\"message\":\"slow down\"}}")));
        assertThatThrownBy(() -> provider(properties()).chat(request("m")).block())
                .isInstanceOfSatisfying(LlmRateLimitException.class, e -> assertThat(e.isRetryable()).isFalse());
    }

    @Test
    void errorObjectInsideTheStreamFailsTheStream() {
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(aResponse().withHeader("Content-Type", "text/event-stream").withBody(sse(
                        "{\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}",
                        "{\"error\":{\"message\":\"model crashed\",\"type\":\"server_error\"}}"))));

        assertThatThrownBy(() -> provider(properties()).stream(request("m")).blockLast())
                .isInstanceOfSatisfying(LlmException.class, e -> assertThat(e.getMessage()).contains("model crashed"));
    }

    private static void stubError(int status, String body) {
        server.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody(body)));
    }
}
