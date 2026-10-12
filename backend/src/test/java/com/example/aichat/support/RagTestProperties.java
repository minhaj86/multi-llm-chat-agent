package com.example.aichat.support;

import com.example.aichat.rag.application.RagProperties;
import com.example.aichat.rag.application.RetrievalMode;
import java.time.Duration;
import org.springframework.util.unit.DataSize;

/**
 * {@link RagProperties} for unit tests with the production defaults, plus targeted overrides.
 */
public final class RagTestProperties {

    private RagTestProperties() {
    }

    public static RagProperties defaults() {
        return with(chunking(800, 150), retrieval(8, 0.70, RetrievalMode.VECTOR), new RagProperties.Context(6000, 8));
    }

    public static RagProperties with(RagProperties.Chunking chunking, RagProperties.Retrieval retrieval,
                                     RagProperties.Context context) {
        return new RagProperties(true, chunking, retrieval, context, embedding("hashing", "http://localhost"),
                new RagProperties.Ingestion(DataSize.ofMegabytes(50), 5_000_000, 2, Duration.ofMinutes(5), 200_000),
                new RagProperties.Reranking(false, "none"), new RagProperties.Vector("pgvector", true, 100));
    }

    public static RagProperties.Chunking chunking(int size, int overlap) {
        return new RagProperties.Chunking("recursive", size, overlap, 3.5);
    }

    public static RagProperties.Retrieval retrieval(int topK, double threshold, RetrievalMode mode) {
        return new RagProperties.Retrieval(topK, threshold, mode, 3, 60, 50);
    }

    public static RagProperties.Embedding embedding(String provider, String baseUrl) {
        return new RagProperties.Embedding(provider, "test-embedding", baseUrl, "test-key", 0, 32,
                Duration.ofSeconds(5), Duration.ofSeconds(2), 3, 8000, null, null);
    }

    public static RagProperties withIngestion(int maxExtractedCharacters, DataSize maxFileSize) {
        RagProperties base = defaults();
        return new RagProperties(true, base.chunking(), base.retrieval(), base.context(), base.embedding(),
                new RagProperties.Ingestion(maxFileSize, maxExtractedCharacters, 2, Duration.ofMinutes(5), 200_000),
                base.reranking(), base.vector());
    }
}
