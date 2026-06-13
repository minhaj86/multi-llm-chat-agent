package com.example.aichat.infrastructure.llm.qwen;

import static com.example.aichat.support.LlmTestSupport.adapterSupport;
import static com.example.aichat.support.LlmTestSupport.compatible;
import static com.example.aichat.support.LlmTestSupport.completion;
import static com.example.aichat.support.LlmTestSupport.reasoning;
import static com.example.aichat.support.LlmTestSupport.request;
import static com.example.aichat.support.LlmTestSupport.sse;
import static com.example.aichat.support.LlmTestSupport.text;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.notContaining;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.aichat.domain.llm.FinishReason;
import com.example.aichat.domain.llm.LlmCapability;
import com.example.aichat.domain.llm.LlmChunk;
import com.example.aichat.domain.llm.LlmRequest;
import com.example.aichat.domain.llm.LlmResponse;
import com.example.aichat.domain.llm.ProviderKind;
import com.example.aichat.domain.llm.ReasoningMode;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class QwenLlmProviderTest {

    @RegisterExtension
    static WireMockExtension server = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

    private QwenLlmProvider provider(String engine) {
        return new QwenLlmProvider("qwen", compatible(server.baseUrl() + "/v1", "qwen", engine, "qwen3:8b"),
                adapterSupport());
    }

    @Test
    void describesItselfAsLocalReasoningProvider() {
        QwenLlmProvider provider = provider("ollama");

        assertThat(provider.descriptor().displayName()).isEqualTo("Qwen Local");
        assertThat(provider.descriptor().kind()).isEqualTo(ProviderKind.LOCAL);
        assertThat(provider.descriptor().capabilities()).contains(LlmCapability.REASONING, LlmCapability.STREAMING);
        assertThat(provider.descriptor().endpoint()).isEqualTo(server.baseUrl() + "/v1");
    }

    @Test
    void streamsReasoningSeparatelyFromTheAnswerAndReportsUsage() {
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(matchingJsonPath("$.stream", equalTo("true")))
                .willReturn(aResponse().withHeader("Content-Type", "text/event-stream").withBody(sse(
                        "{\"id\":\"c1\",\"model\":\"qwen3:8b\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"\",\"reasoning\":\"Thinking\"}}]}",
                        "{\"id\":\"c1\",\"model\":\"qwen3:8b\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"}}]}",
                        "{\"id\":\"c1\",\"model\":\"qwen3:8b\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\" world\"},\"finish_reason\":\"stop\"}]}",
                        "{\"id\":\"c1\",\"model\":\"qwen3:8b\",\"choices\":[],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":5,\"total_tokens\":17}}",
                        "[DONE]"))));

        List<LlmChunk> chunks = provider("ollama").stream(request("qwen3:8b")).collectList().block();

        assertThat(reasoning(chunks)).isEqualTo("Thinking");
        assertThat(text(chunks)).isEqualTo("Hello world");
        LlmChunk.Completed done = completion(chunks);
        assertThat(done.usage().inputTokens()).isEqualTo(12);
        assertThat(done.usage().outputTokens()).isEqualTo(5);
        assertThat(done.finishReason()).isEqualTo(FinishReason.STOP);
        server.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withHeader("Authorization", equalTo("Bearer local"))
                .withHeader("X-Request-Id", equalTo("test-correlation-id"))
                .withRequestBody(matchingJsonPath("$.stream_options.include_usage", equalTo("true")))
                .withRequestBody(matchingJsonPath("$.messages[0].role", equalTo("system")))
                .withRequestBody(matchingJsonPath("$.messages[1].content", equalTo("Hello?"))));
    }

    @Test
    void parsesInlineThinkTagsFromServersWithoutReasoningParser() {
        server.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(aResponse().withHeader("Content-Type", "text/event-stream").withBody(sse(
                        "{\"choices\":[{\"delta\":{\"content\":\"<think>\"}}]}",
                        "{\"choices\":[{\"delta\":{\"content\":\"plan the answer\"}}]}",
                        "{\"choices\":[{\"delta\":{\"content\":\"</think>\\n\\nParis\"}}]}",
                        "{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}",
                        "[DONE]"))));

        List<LlmChunk> chunks = provider("vllm").stream(request("qwen")).collectList().block();

        assertThat(reasoning(chunks)).isEqualTo("plan the answer");
        assertThat(text(chunks)).isEqualTo("Paris");
    }

    @Test
    void disablesThinkingTheOllamaWay() {
        stubCompletion();
        LlmRequest request = request("qwen3:8b");

        provider("ollama").chat(request.withOptions(request.options().withReasoning(ReasoningMode.DISABLED))).block();

        server.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(matchingJsonPath("$.reasoning_effort", equalTo("none")))
                .withRequestBody(notContaining("/no_think")));
    }

    @Test
    void disablesThinkingThroughTheChatTemplateOnVllm() {
        stubCompletion();
        LlmRequest request = request("qwen");

        provider("vllm").chat(request.withOptions(request.options().withReasoning(ReasoningMode.DISABLED))).block();

        server.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(matchingJsonPath("$.chat_template_kwargs.enable_thinking", equalTo("false")))
                .withRequestBody(notContaining("reasoning_effort")));
    }

    @Test
    void usesTheNoThinkSoftSwitchOnUnknownEngines() {
        stubCompletion();
        LlmRequest request = request("qwen");

        provider("generic").chat(request.withOptions(request.options().withReasoning(ReasoningMode.DISABLED))).block();

        server.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(matchingJsonPath("$.messages[1].content", equalTo("Hello? /no_think"))));
    }

    @Test
    void keepsDefaultReasoningUntouched() {
        stubCompletion();

        LlmResponse response = provider("ollama").chat(request("qwen3:8b")).block();

        assertThat(response.content()).isEqualTo("Paris");
        assertThat(response.reasoning()).isEqualTo("France's capital");
        server.verify(postRequestedFor(urlEqualTo("/v1/chat/completions"))
                .withRequestBody(notContaining("reasoning_effort"))
                .withRequestBody(matchingJsonPath("$.stream", equalTo("false"))));
    }

    private static void stubCompletion() {
        server.stubFor(post(urlEqualTo("/v1/chat/completions")).willReturn(okJson("""
                {"id":"c2","model":"qwen3:8b","choices":[{"index":0,"message":{"role":"assistant",
                 "content":"Paris","reasoning":"France's capital"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":10,"completion_tokens":2}}""")));
    }
}
