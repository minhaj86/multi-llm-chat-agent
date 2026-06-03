package com.example.aichat.infrastructure.llm;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

/**
 * Builds one {@link WebClient} per provider with its own connection pool and connect timeout. Credentials are set
 * as default headers here and never logged. Response timeouts are applied per request by the resilience layer.
 */
@Component
public class LlmWebClientFactory {

    private static final int MAX_IN_MEMORY_SIZE = 16 * 1024 * 1024;

    private final WebClient.Builder builder;

    public LlmWebClientFactory(WebClient.Builder builder) {
        this.builder = builder;
    }

    public WebClient create(String providerId, String baseUrl, ProviderProperties properties) {
        ConnectionProvider pool = ConnectionProvider.builder("llm-" + providerId)
                .maxConnections(200)
                .pendingAcquireTimeout(Duration.ofSeconds(30))
                .maxIdleTime(Duration.ofSeconds(30))
                .evictInBackground(Duration.ofSeconds(60))
                .build();
        HttpClient httpClient = HttpClient.create(pool)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) properties.getConnectTimeout().toMillis());
        return builder.clone()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(MAX_IN_MEMORY_SIZE))
                .defaultHeaders(headers -> {
                    String apiKey = properties.getApiKey();
                    if (apiKey != null && !apiKey.isBlank()) {
                        headers.setBearerAuth(apiKey.strip());
                    }
                    properties.getHeaders().forEach(headers::set);
                })
                .build();
    }
}
