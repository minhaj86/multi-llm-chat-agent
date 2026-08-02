package com.example.aichat.configuration;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CORS for the SPA when it is served from a different origin (e.g. the Vite dev server). Bearer tokens are sent in
 * the {@code Authorization} header, so credentials (cookies) are not allowed.
 */
@Configuration(proxyBeanMethods = false)
public class WebConfiguration {

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Correlation-Id",
                "X-Request-Id", "Last-Event-ID"));
        configuration.setExposedHeaders(List.of("X-Correlation-Id", "Retry-After", "X-RateLimit-Remaining"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(properties.maxAge());
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
