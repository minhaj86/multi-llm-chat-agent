package com.example.aichat.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.example.aichat.application.provider.ProviderHealthMonitor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests: real Spring context and PostgreSQL (Testcontainers, Flyway migrations),
 * with every LLM provider replaced by WireMock. Containers are started once and shared by all test classes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");
    protected static final WireMockServer WIREMOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    protected static final int DEAD_PORT = freePort();

    static {
        POSTGRES.start();
        WIREMOCK.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("llm.providers.openai.base-url", () -> WIREMOCK.baseUrl() + "/openai");
        registry.add("llm.providers.qwen.base-url", () -> WIREMOCK.baseUrl() + "/qwen/v1");
        registry.add("llm.providers.llama.base-url", () -> WIREMOCK.baseUrl() + "/llama/v1");
        registry.add("llm.providers.offline.base-url", () -> "http://127.0.0.1:" + DEAD_PORT + "/v1");
    }

    @Autowired
    protected TestRestTemplate rest;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected ProviderHealthMonitor healthMonitor;

    @BeforeEach
    void resetProviders() {
        jdbc.update("delete from provider_settings");
        WIREMOCK.resetAll();
        WIREMOCK.stubFor(get(urlEqualTo("/qwen/v1/models")).willReturn(okJson("{\"data\":[{\"id\":\"qwen3:8b\"},{\"id\":\"qwen3:32b\"}]}")));
        WIREMOCK.stubFor(get(urlEqualTo("/llama/v1/models")).willReturn(okJson("{\"data\":[{\"id\":\"llama3.1:8b\"}]}")));
        WIREMOCK.stubFor(get(urlEqualTo("/openai/v1/models")).willReturn(okJson("{\"data\":[{\"id\":\"gpt-5-mini\"}]}")));
        stubTitle("qwen");
        stubTitle("llama");
        stubStream("qwen", "Thinking", "Hello", " from Qwen");
        stubStream("llama", null, "Hello", " from Llama");
    }

    // ---------------------------------------------------------------------------------------------- provider stubs

    /** Streaming chat completion: optional reasoning, then content parts, then usage. */
    protected static void stubStream(String provider, String reasoning, String... parts) {
        List<String> events = new ArrayList<>();
        if (reasoning != null) {
            events.add("{\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":\"\",\"reasoning\":\"" + reasoning + "\"}}]}");
        }
        for (String part : parts) {
            events.add("{\"choices\":[{\"delta\":{\"content\":\"" + part + "\"}}]}");
        }
        events.add("{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}");
        events.add("{\"choices\":[],\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":5}}");
        events.add("[DONE]");
        WIREMOCK.stubFor(post(urlEqualTo("/" + provider + "/v1/chat/completions")).atPriority(5)
                .withRequestBody(matchingJsonPath("$.stream", equalTo("true")))
                .willReturn(aResponse().withHeader("Content-Type", "text/event-stream")
                        .withBody(LlmTestSupport.sse(events.toArray(String[]::new)))));
    }

    protected static void stubTitle(String provider) {
        WIREMOCK.stubFor(post(urlEqualTo("/" + provider + "/v1/chat/completions")).atPriority(5)
                .withRequestBody(matchingJsonPath("$.stream", equalTo("false")))
                .withRequestBody(containing("concise titles"))
                .willReturn(okJson("{\"choices\":[{\"message\":{\"content\":\"Generated Title\"},\"finish_reason\":\"stop\"}],"
                        + "\"usage\":{\"prompt_tokens\":30,\"completion_tokens\":3}}")));
    }

    // ---------------------------------------------------------------------------------------------- HTTP helpers

    protected String register(String username) {
        ResponseEntity<String> response = rest.postForEntity("/api/auth/register", json(Map.of(
                "username", username, "email", username + "@example.com", "password", "Secret-pass-123"), null),
                String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new AssertionError("Registration failed: " + response);
        }
        return read(response.getBody()).get("accessToken").asText();
    }

    protected String login(String login, String password) {
        ResponseEntity<String> response = rest.postForEntity("/api/auth/login",
                json(Map.of("login", login, "password", password), null), String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new AssertionError("Login failed: " + response);
        }
        return read(response.getBody()).get("accessToken").asText();
    }

    protected String adminToken() {
        return login("admin", "Admin-pass-123");
    }

    protected HttpEntity<Object> json(Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON));
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return new HttpEntity<>(body, headers);
    }

    protected ResponseEntity<String> call(HttpMethod method, String path, Object body, String token) {
        return rest.exchange(path, method, json(body, token), String.class);
    }

    /** POSTs to a streaming endpoint like a browser does (SSE, with problem+json for errors before streaming). */
    protected ResponseEntity<String> callStream(String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_PROBLEM_JSON));
        headers.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    protected JsonNode getJson(String path, String token) {
        ResponseEntity<String> response = call(HttpMethod.GET, path, null, token);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new AssertionError("GET " + path + " failed: " + response);
        }
        return read(response.getBody());
    }

    /** POSTs to a Server-Sent-Events endpoint and returns all events once the stream completes. */
    protected List<SseEvent> streamEvents(String path, Object body, String token) {
        ResponseEntity<String> response = callStream(path, body, token);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new AssertionError("Stream request failed: " + response);
        }
        return SseEvent.parse(response.getBody(), objectMapper);
    }

    protected JsonNode read(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
