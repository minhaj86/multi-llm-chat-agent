package com.example.aichat.configuration;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Allowed browser origins ({@code app.cors.allowed-origins}, comma separated via {@code CORS_ALLOWED_ORIGINS}).
 */
@ConfigurationProperties("app.cors")
public record CorsProperties(List<String> allowedOrigins, @DefaultValue("PT1H") Duration maxAge) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins.stream()
                .map(String::strip)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }
}
