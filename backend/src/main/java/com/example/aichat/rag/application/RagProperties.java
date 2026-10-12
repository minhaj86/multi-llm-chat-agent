package com.example.aichat.rag.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Configuration of the RAG subsystem ({@code rag.*}). Every value can be overridden with environment variables.
 *
 * @param enabled master switch; when false chat never retrieves and the knowledge UI is read-only
 */
@ConfigurationProperties("rag")
public record RagProperties(@DefaultValue("true") boolean enabled,
                            @DefaultValue Chunking chunking,
                            @DefaultValue Retrieval retrieval,
                            @DefaultValue Context context,
                            @DefaultValue Embedding embedding,
                            @DefaultValue Ingestion ingestion,
                            @DefaultValue Reranking reranking,
                            @DefaultValue Vector vector) {

    /**
     * @param chunkSize     target chunk size in estimated tokens
     * @param overlap       tokens repeated at the start of the next chunk (keeps sentences that span a boundary)
     * @param charsPerToken heuristic used to convert tokens to characters
     */
    public record Chunking(@DefaultValue("recursive") String strategy,
                           @DefaultValue("800") int chunkSize,
                           @DefaultValue("150") int overlap,
                           @DefaultValue("3.5") double charsPerToken) {

        public Chunking {
            if (chunkSize < 50) {
                throw new IllegalArgumentException("rag.chunking.chunk-size must be at least 50");
            }
            if (overlap < 0 || overlap >= chunkSize) {
                throw new IllegalArgumentException("rag.chunking.overlap must be between 0 and chunk-size - 1");
            }
            if (charsPerToken <= 0) {
                throw new IllegalArgumentException("rag.chunking.chars-per-token must be positive");
            }
        }

        public int chunkSizeCharacters() {
            return (int) Math.round(chunkSize * charsPerToken);
        }

        public int overlapCharacters() {
            return (int) Math.round(overlap * charsPerToken);
        }
    }

    /**
     * @param similarityThreshold minimum cosine similarity of a vector match; tune per embedding model
     * @param candidateMultiplier candidates fetched per requested result before thresholding/reranking
     * @param rrfK                constant of reciprocal rank fusion (hybrid mode)
     */
    public record Retrieval(@DefaultValue("8") int topK,
                            @DefaultValue("0.70") double similarityThreshold,
                            @DefaultValue("VECTOR") RetrievalMode mode,
                            @DefaultValue("3") int candidateMultiplier,
                            @DefaultValue("60") int rrfK,
                            @DefaultValue("50") int maxTopK) {

        public Retrieval {
            if (topK < 1 || topK > maxTopK) {
                throw new IllegalArgumentException("rag.retrieval.top-k must be between 1 and " + maxTopK);
            }
            if (similarityThreshold < -1 || similarityThreshold > 1) {
                throw new IllegalArgumentException("rag.retrieval.similarity-threshold must be between -1 and 1");
            }
            candidateMultiplier = Math.max(1, candidateMultiplier);
        }
    }

    /**
     * @param maxTokens budget for retrieved knowledge in one LLM request
     * @param maxChunks maximum number of chunks in one LLM request
     */
    public record Context(@DefaultValue("6000") int maxTokens, @DefaultValue("8") int maxChunks) {

        public Context {
            if (maxTokens < 200) {
                throw new IllegalArgumentException("rag.context.max-tokens must be at least 200");
            }
            if (maxChunks < 1) {
                throw new IllegalArgumentException("rag.context.max-chunks must be positive");
            }
        }
    }

    /**
     * @param provider       {@code openai}, {@code openai-compatible} (Ollama, vLLM, LM Studio, LocalAI, llama.cpp),
     *                       {@code hashing} (offline keyword-level embeddings), or a custom factory's type
     * @param dimensions     requested vector size for models that support shortening (0 = model default)
     * @param queryPrefix    prepended to questions (e.g. instruction-tuned models such as Qwen3-Embedding)
     * @param documentPrefix prepended to chunks before embedding
     */
    public record Embedding(@DefaultValue("openai") String provider,
                            @DefaultValue("text-embedding-3-small") String model,
                            @DefaultValue("https://api.openai.com/v1") String baseUrl,
                            String apiKey,
                            @DefaultValue("0") int dimensions,
                            @DefaultValue("32") int batchSize,
                            @DefaultValue("PT60S") Duration timeout,
                            @DefaultValue("PT5S") Duration connectTimeout,
                            @DefaultValue("3") int maxAttempts,
                            @DefaultValue("8000") int maxInputCharacters,
                            String queryPrefix,
                            String documentPrefix) {

        public Embedding {
            batchSize = Math.max(1, batchSize);
            maxAttempts = Math.max(1, maxAttempts);
            maxInputCharacters = Math.max(256, maxInputCharacters);
        }
    }

    /**
     * @param maxFileSize            largest accepted upload
     * @param maxExtractedCharacters protects against decompression bombs and gigantic documents
     * @param concurrency            documents processed in parallel by the background ingestion workers
     */
    public record Ingestion(@DefaultValue("50MB") DataSize maxFileSize,
                            @DefaultValue("5000000") int maxExtractedCharacters,
                            @DefaultValue("2") int concurrency,
                            @DefaultValue("PT5M") Duration extractionTimeout,
                            @DefaultValue("200000") int maxTextEntryCharacters) {

        public Ingestion {
            concurrency = Math.max(1, concurrency);
        }
    }

    public record Reranking(@DefaultValue("false") boolean enabled, @DefaultValue("none") String provider) {
    }

    /**
     * @param createIndex create an HNSW index per embedding dimension (pgvector, up to 2000 dimensions)
     * @param efSearch    HNSW search breadth; higher = better recall, slower
     */
    public record Vector(@DefaultValue("pgvector") String store,
                         @DefaultValue("true") boolean createIndex,
                         @DefaultValue("100") int efSearch) {
    }
}
