package com.example.aichat.rag.infrastructure.embedding;

import com.example.aichat.rag.domain.EmbeddingProvider;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Offline, deterministic embeddings based on feature hashing of words and word pairs (TF-weighted, signed,
 * L2-normalised). Similarity reflects shared vocabulary rather than meaning, comparable to keyword search.
 * Useful when no embedding model is reachable (air-gapped demos, CI); for production quality use a real
 * embedding model and reindex.
 */
public class HashingEmbeddingProvider implements EmbeddingProvider {

    public static final int DEFAULT_DIMENSIONS = 1024;
    private static final double PAIR_WEIGHT = 0.5;
    private static final Set<String> STOP_WORDS = Set.of("a", "about", "after", "all", "also", "an", "and", "any",
            "are", "as", "at", "be", "been", "but", "by", "can", "could", "did", "do", "does", "for", "from", "had",
            "has", "have", "how", "i", "if", "in", "into", "is", "it", "its", "may", "me", "more", "most", "my",
            "no", "not", "of", "on", "or", "our", "out", "please", "should", "so", "some", "such", "than", "that",
            "the", "their", "them", "then", "there", "these", "they", "this", "those", "to", "up", "us", "was",
            "we", "were", "what", "when", "where", "which", "while", "who", "why", "will", "with", "would", "you",
            "your");

    private final String providerId;
    private final String modelId;
    private final int dimensions;

    public HashingEmbeddingProvider(int dimensions) {
        this("hashing", "hashing-" + dimensions, dimensions);
    }

    public HashingEmbeddingProvider(String providerId, String modelId, int dimensions) {
        if (dimensions < 16) {
            throw new IllegalArgumentException("Hashing embeddings need at least 16 dimensions");
        }
        this.providerId = providerId;
        this.modelId = modelId;
        this.dimensions = dimensions;
    }

    @Override
    public String providerId() {
        return providerId;
    }

    @Override
    public String modelId() {
        return modelId;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }

    float[] embed(String text) {
        List<String> words = words(text);
        Map<String, Double> features = new HashMap<>();
        for (int i = 0; i < words.size(); i++) {
            features.merge(words.get(i), 1.0, Double::sum);
            if (i > 0) {
                features.merge(words.get(i - 1) + ' ' + words.get(i), PAIR_WEIGHT, Double::sum);
            }
        }
        double[] vector = new double[dimensions];
        features.forEach((feature, frequency) -> {
            long hash = fnv1a64(feature);
            int index = (int) Long.remainderUnsigned(hash, dimensions);
            double sign = (hash >>> 63) == 0 ? 1.0 : -1.0;
            vector[index] += sign * (1.0 + Math.log(frequency));
        });
        double norm = 0;
        for (double value : vector) {
            norm += value * value;
        }
        float[] result = new float[dimensions];
        if (norm == 0) {
            result[0] = 1f;
            return result;
        }
        double scale = 1.0 / Math.sqrt(norm);
        for (int i = 0; i < dimensions; i++) {
            result[i] = (float) (vector[i] * scale);
        }
        return result;
    }

    static List<String> words(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        List<String> words = new ArrayList<>();
        for (String raw : normalized.split("[^\\p{L}\\p{N}]+")) {
            if (raw.length() < 2 || STOP_WORDS.contains(raw)) {
                continue;
            }
            words.add(stem(raw));
        }
        return words;
    }

    /** Very light, language-tolerant suffix folding so that "rates" matches "rate" and "currencies" "currency". */
    static String stem(String word) {
        if (word.length() > 4 && word.endsWith("ies")) {
            return word.substring(0, word.length() - 3) + "y";
        }
        if (word.length() > 3 && word.endsWith("s") && !word.endsWith("ss") && !word.endsWith("us")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }

    private static long fnv1a64(String value) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= b & 0xff;
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
