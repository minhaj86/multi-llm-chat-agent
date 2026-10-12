package com.example.aichat.rag.infrastructure.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class HashingEmbeddingProviderTest {

    private final HashingEmbeddingProvider provider = new HashingEmbeddingProvider(512);

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / Math.sqrt(na * nb);
    }

    @Test
    void producesDeterministicNormalisedVectors() {
        List<float[]> first = provider.embed(List.of("Currency conversion uses daily exchange rates."));
        List<float[]> second = provider.embed(List.of("Currency conversion uses daily exchange rates."));

        assertThat(first.getFirst()).hasSize(512).containsExactly(second.getFirst());
        double norm = 0;
        for (float value : first.getFirst()) {
            norm += value * value;
        }
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-4));
        assertThat(provider.modelId()).isEqualTo("hashing-512");
    }

    @Test
    void textsSharingVocabularyAreMoreSimilar() {
        List<float[]> vectors = provider.embed(List.of(
                "How does the pricing service convert currencies?",
                "The pricing service converts currency with the daily exchange rate.",
                "The cafeteria opens at eight in the morning."));

        double related = cosine(vectors.get(0), vectors.get(1));
        double unrelated = cosine(vectors.get(0), vectors.get(2));
        assertThat(related).isGreaterThan(0.3).isGreaterThan(unrelated + 0.2);
    }

    @Test
    void foldsSimplePluralsAndIgnoresStopWords() {
        assertThat(HashingEmbeddingProvider.words("The Rates of the Currencies"))
                .containsExactly("rate", "currency");
    }
}
