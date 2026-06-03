package com.example.aichat.infrastructure.llm;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Root of the provider configuration ({@code llm.*}). Each entry of {@code providers} becomes one provider
 * instance; the map key is the provider id used throughout the API.
 *
 * <pre>
 * llm:
 *   default-provider: openai
 *   providers:
 *     qwen:
 *       type: openai-compatible
 *       family: qwen
 *       base-url: http://localhost:8001/v1
 *       default-model: qwen
 * </pre>
 */
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /** Provider used when a request does not specify one. */
    private String defaultProvider;

    private Map<String, ProviderProperties> providers = new LinkedHashMap<>();

    public String getDefaultProvider() {
        return defaultProvider;
    }

    public void setDefaultProvider(String defaultProvider) {
        this.defaultProvider = defaultProvider;
    }

    public Map<String, ProviderProperties> getProviders() {
        return providers;
    }

    public void setProviders(Map<String, ProviderProperties> providers) {
        this.providers = providers;
    }
}
