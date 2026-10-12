package com.example.aichat.support;

import com.example.aichat.rag.application.EmbeddingProviderFactory;
import com.example.aichat.rag.application.RagProperties;
import com.example.aichat.rag.domain.EmbeddingException;
import com.example.aichat.rag.domain.EmbeddingProvider;
import com.example.aichat.rag.infrastructure.embedding.HashingEmbeddingProvider;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Deterministic embeddings for tests ({@code rag.embedding.provider=fake}): texts sharing vocabulary get similar
 * vectors, so semantic-search assertions are stable, and no external embedding API is ever called. Tests can count
 * calls and simulate an outage.
 */
public class FakeEmbeddingProvider implements EmbeddingProvider {

    public static final int DIMENSIONS = 256;

    private final HashingEmbeddingProvider delegate = new HashingEmbeddingProvider("fake", "fake-embedding",
            DIMENSIONS);
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger texts = new AtomicInteger();
    private final AtomicBoolean failing = new AtomicBoolean();

    @Override
    public String providerId() {
        return "fake";
    }

    @Override
    public String modelId() {
        return "fake-embedding";
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public List<float[]> embed(List<String> inputs) {
        calls.incrementAndGet();
        texts.addAndGet(inputs.size());
        if (failing.get()) {
            throw new EmbeddingException("Fake embedding service is unavailable", true, null);
        }
        return delegate.embed(inputs);
    }

    public int calls() {
        return calls.get();
    }

    public int embeddedTexts() {
        return texts.get();
    }

    public void failing(boolean value) {
        failing.set(value);
    }

    public void reset() {
        calls.set(0);
        texts.set(0);
        failing.set(false);
    }

    /** Registers the fake provider as embedding provider type {@code fake}. */
    @TestConfiguration(proxyBeanMethods = false)
    public static class Configuration {

        @Bean
        EmbeddingProviderFactory fakeEmbeddingProviderFactory() {
            FakeEmbeddingProvider provider = new FakeEmbeddingProvider();
            return new EmbeddingProviderFactory() {
                @Override
                public Set<String> types() {
                    return Set.of("fake");
                }

                @Override
                public EmbeddingProvider create(RagProperties.Embedding configuration) {
                    return provider;
                }
            };
        }
    }
}
