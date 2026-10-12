package com.example.aichat.rag.application;

import com.example.aichat.rag.domain.EmbeddingException;
import com.example.aichat.rag.domain.EmbeddingProvider;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Application-level access to the embedding model: applies the configured query/document prefixes (needed by
 * instruction-tuned models such as Qwen3-Embedding or nomic-embed-text), validates results and records metrics.
 */
@Service
public class EmbeddingService {

    private final EmbeddingProvider provider;
    private final RagMetrics metrics;
    private final String queryPrefix;
    private final String documentPrefix;

    public EmbeddingService(EmbeddingProvider provider, RagProperties properties, RagMetrics metrics) {
        this.provider = provider;
        this.metrics = metrics;
        this.queryPrefix = unescape(properties.embedding().queryPrefix());
        this.documentPrefix = unescape(properties.embedding().documentPrefix());
    }

    public String providerId() {
        return provider.providerId();
    }

    public String modelId() {
        return provider.modelId();
    }

    public int dimensions() {
        return provider.dimensions();
    }

    public List<float[]> embedDocuments(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        List<String> inputs = documentPrefix.isEmpty() ? texts
                : texts.stream().map(text -> documentPrefix + text).toList();
        return embed(inputs, "document");
    }

    public float[] embedQuery(String query) {
        return embed(List.of(queryPrefix + query), "query").getFirst();
    }

    private List<float[]> embed(List<String> inputs, String kind) {
        long start = System.nanoTime();
        try {
            List<float[]> vectors = provider.embed(inputs);
            if (vectors.size() != inputs.size()) {
                throw new EmbeddingException("The embedding provider returned " + vectors.size()
                        + " vectors for " + inputs.size() + " inputs", false, null);
            }
            metrics.embedding(provider.providerId(), provider.modelId(), kind, "success", inputs.size(),
                    System.nanoTime() - start);
            return vectors;
        } catch (RuntimeException e) {
            metrics.embedding(provider.providerId(), provider.modelId(), kind, "error", inputs.size(),
                    System.nanoTime() - start);
            throw e instanceof EmbeddingException ? e
                    : new EmbeddingException("Embedding failed: " + e.getMessage(), false, e);
        }
    }

    /** Environment variables cannot carry real line breaks easily, so {@code \n} and {@code \t} are unescaped. */
    static String unescape(String value) {
        return value == null ? "" : value.replace("\\n", "\n").replace("\\t", "\t");
    }
}
