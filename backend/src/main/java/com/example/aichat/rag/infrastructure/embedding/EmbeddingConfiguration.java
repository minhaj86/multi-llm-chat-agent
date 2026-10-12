package com.example.aichat.rag.infrastructure.embedding;

import com.example.aichat.rag.application.EmbeddingProviderFactory;
import com.example.aichat.rag.application.RagProperties;
import com.example.aichat.rag.domain.EmbeddingProvider;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Selects the embedding provider from {@code rag.embedding.provider}. The choice is independent of the chat
 * providers: any chat model can be combined with any embedding model.
 */
@Configuration(proxyBeanMethods = false)
public class EmbeddingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingConfiguration.class);

    @Bean
    public EmbeddingProvider embeddingProvider(RagProperties properties, List<EmbeddingProviderFactory> factories) {
        String type = properties.embedding().provider() == null ? ""
                : properties.embedding().provider().strip().toLowerCase(Locale.ROOT);
        EmbeddingProviderFactory factory = factories.stream().filter(f -> f.types().contains(type)).findFirst()
                .orElseThrow(() -> {
                    TreeSet<String> available = new TreeSet<>();
                    factories.forEach(f -> available.addAll(f.types()));
                    return new IllegalStateException("No embedding provider for rag.embedding.provider='" + type
                            + "'. Available: " + available);
                });
        EmbeddingProvider provider = factory.create(properties.embedding());
        log.info("Embedding provider '{}' with model '{}' ({} dimensions{})", provider.providerId(),
                provider.modelId(), provider.dimensions() == 0 ? "auto" : provider.dimensions(),
                properties.enabled() ? "" : ", RAG disabled");
        return provider;
    }
}
