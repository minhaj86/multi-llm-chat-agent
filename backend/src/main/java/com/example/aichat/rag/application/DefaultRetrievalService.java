package com.example.aichat.rag.application;

import com.example.aichat.common.Texts;
import com.example.aichat.common.Times;
import com.example.aichat.rag.domain.KeywordSearcher;
import com.example.aichat.rag.domain.KeywordSearcher.KeywordSearchResult;
import com.example.aichat.rag.domain.KnowledgeChunk;
import com.example.aichat.rag.domain.KnowledgeChunkRepository;
import com.example.aichat.rag.domain.KnowledgeCollection;
import com.example.aichat.rag.domain.KnowledgeSource;
import com.example.aichat.rag.domain.KnowledgeSourceRepository;
import com.example.aichat.rag.domain.VectorFilter;
import com.example.aichat.rag.domain.VectorSearchRequest;
import com.example.aichat.rag.domain.VectorSearchResult;
import com.example.aichat.rag.domain.VectorStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Retrieval pipeline: resolve allowed collections (enabled ones, intersected with the requested ones) -> embed the
 * query -> vector search (plus full-text search in hybrid mode, merged with reciprocal rank fusion) -> similarity
 * threshold -> reranking -> top-k. Results only include enabled, indexed sources of enabled collections.
 */
@Service
public class DefaultRetrievalService implements RetrievalService {

    private final EmbeddingService embedding;
    private final VectorStore vectorStore;
    private final KeywordSearcher keywordSearcher;
    private final KnowledgeChunkRepository chunks;
    private final KnowledgeSourceRepository sources;
    private final KnowledgeCollectionService collections;
    private final RerankingService reranker;
    private final RagProperties properties;
    private final RagMetrics metrics;

    public DefaultRetrievalService(EmbeddingService embedding, VectorStore vectorStore, KeywordSearcher keywordSearcher,
                                   KnowledgeChunkRepository chunks, KnowledgeSourceRepository sources,
                                   KnowledgeCollectionService collections, List<RerankingService> rerankers,
                                   RagProperties properties, RagMetrics metrics) {
        this.embedding = embedding;
        this.vectorStore = vectorStore;
        this.keywordSearcher = keywordSearcher;
        this.chunks = chunks;
        this.sources = sources;
        this.collections = collections;
        this.properties = properties;
        this.metrics = metrics;
        this.reranker = selectReranker(rerankers, properties.reranking());
    }

    @Override
    public List<RetrievedChunk> retrieve(String query, RetrievalOptions options) {
        return search(query, options).chunks();
    }

    @Override
    public RetrievalResult search(String query, RetrievalOptions options) {
        long started = System.nanoTime();
        RetrievalOptions opts = options == null ? RetrievalOptions.defaults() : options;
        RetrievalMode mode = opts.mode() == null ? properties.retrieval().mode() : opts.mode();
        int topK = Math.clamp(opts.topK() == null ? properties.retrieval().topK() : opts.topK(), 1,
                properties.retrieval().maxTopK());
        double threshold = opts.similarityThreshold() == null ? properties.retrieval().similarityThreshold()
                : opts.similarityThreshold();
        Map<UUID, KnowledgeCollection> allowed = allowedCollections(opts.collectionIds());
        String collectionLabel = collectionLabel(allowed, opts.collectionIds());
        if (allowed.isEmpty() || Texts.isBlank(query)) {
            metrics.retrieval(embedding.providerId(), embedding.modelId(), collectionLabel, "empty", 0,
                    System.nanoTime() - started);
            return new RetrievalResult(query, List.of(), 0, allowed.keySet(), mode, topK, threshold,
                    embedding.providerId(), embedding.modelId(), reranker.name(), 0, 0, Times.elapsedMillis(started));
        }
        VectorFilter filter = new VectorFilter(allowed.keySet(), opts.sourceIds(), normalize(opts.tags()));
        int candidatesLimit = topK * properties.retrieval().candidateMultiplier();
        try {
            long embeddingStart = System.nanoTime();
            float[] queryVector = embedding.embedQuery(query.strip());
            long embeddingMs = Times.elapsedMillis(embeddingStart);

            long searchStart = System.nanoTime();
            List<VectorSearchResult> vectorHits = vectorStore.search(new VectorSearchRequest(queryVector,
                    embedding.modelId(), candidatesLimit, filter));
            List<KeywordSearchResult> keywordHits = mode == RetrievalMode.HYBRID
                    ? keywordSearcher.search(query, filter, candidatesLimit) : List.of();
            List<Candidate> candidates = merge(vectorHits, keywordHits, threshold, topK, mode);
            List<RetrievedChunk> resolved = resolve(candidates, allowed);
            List<RetrievedChunk> ranked = reranker.rerank(query, resolved).stream().limit(topK).toList();
            long searchMs = Times.elapsedMillis(searchStart);

            metrics.retrieval(embedding.providerId(), embedding.modelId(), collectionLabel,
                    ranked.isEmpty() ? "empty" : "success", ranked.size(), System.nanoTime() - started);
            return new RetrievalResult(query, ranked, vectorHits.size() + keywordHits.size(), allowed.keySet(), mode,
                    topK, threshold, embedding.providerId(), embedding.modelId(), reranker.name(), embeddingMs,
                    searchMs, Times.elapsedMillis(started));
        } catch (RuntimeException e) {
            metrics.retrieval(embedding.providerId(), embedding.modelId(), collectionLabel, "error", 0,
                    System.nanoTime() - started);
            throw e;
        }
    }

    private Map<UUID, KnowledgeCollection> allowedCollections(Set<UUID> requested) {
        return collections.enabled().stream()
                .filter(c -> requested.isEmpty() || requested.contains(c.id()))
                .collect(Collectors.toMap(KnowledgeCollection::id, Function.identity(), (a, b) -> a,
                        LinkedHashMap::new));
    }

    /**
     * Vector hits below the threshold are dropped. In hybrid mode the remaining vector hits and the best keyword
     * hits are fused with reciprocal rank fusion (score = sum of 1 / (k + rank)).
     */
    private List<Candidate> merge(List<VectorSearchResult> vectorHits, List<KeywordSearchResult> keywordHits,
                                  double threshold, int topK, RetrievalMode mode) {
        List<VectorSearchResult> relevant = vectorHits.stream().filter(hit -> hit.similarity() >= threshold).toList();
        if (mode != RetrievalMode.HYBRID) {
            return relevant.stream().map(hit -> new Candidate(hit.chunkId(), hit.similarity(), null,
                    hit.similarity())).toList();
        }
        int k = properties.retrieval().rrfK();
        Map<UUID, Candidate> fused = new HashMap<>();
        for (int i = 0; i < relevant.size(); i++) {
            VectorSearchResult hit = relevant.get(i);
            fused.put(hit.chunkId(), new Candidate(hit.chunkId(), hit.similarity(), null, 1.0 / (k + i + 1)));
        }
        List<KeywordSearchResult> bestKeyword = keywordHits.stream().limit(topK).toList();
        for (int i = 0; i < bestKeyword.size(); i++) {
            KeywordSearchResult hit = bestKeyword.get(i);
            double contribution = 1.0 / (k + i + 1);
            fused.merge(hit.chunkId(), new Candidate(hit.chunkId(), null, hit.score(), contribution),
                    (a, b) -> new Candidate(a.chunkId(), a.similarity(), b.keywordScore(), a.score() + b.score()));
        }
        return fused.values().stream().sorted(Comparator.comparingDouble(Candidate::score).reversed()
                .thenComparing(c -> c.similarity() == null ? -2.0 : c.similarity(), Comparator.reverseOrder())
                .thenComparing(c -> c.keywordScore() == null ? -1.0 : c.keywordScore(), Comparator.reverseOrder())
                .thenComparing(Candidate::chunkId)).toList();
    }

    private List<RetrievedChunk> resolve(List<Candidate> candidates, Map<UUID, KnowledgeCollection> allowed) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<UUID, KnowledgeChunk> chunkById = chunks.findByIds(candidates.stream().map(Candidate::chunkId).toList())
                .stream().collect(Collectors.toMap(KnowledgeChunk::id, Function.identity()));
        Map<UUID, KnowledgeSource> sourceById = sources.findByIds(chunkById.values().stream()
                .map(KnowledgeChunk::sourceId).collect(Collectors.toCollection(LinkedHashSet::new)));
        List<RetrievedChunk> result = new ArrayList<>();
        for (Candidate candidate : candidates) {
            KnowledgeChunk chunk = chunkById.get(candidate.chunkId());
            KnowledgeSource source = chunk == null ? null : sourceById.get(chunk.sourceId());
            if (chunk == null || source == null || !source.isSearchable()
                    || !allowed.containsKey(chunk.collectionId())) {
                continue;
            }
            result.add(new RetrievedChunk(chunk.id(), source.id(), source.title(), source.type(), source.sourceUrl(),
                    chunk.collectionId(), allowed.get(chunk.collectionId()).name(), chunk.chunkIndex(),
                    chunk.content(), chunk.tokenCount(), chunk.pageNumber(), chunk.sectionTitle(),
                    candidate.similarity(), candidate.keywordScore(), null, candidate.score()));
        }
        return result;
    }

    private static Set<String> normalize(Set<String> tags) {
        return tags.stream().map(t -> t.strip().toLowerCase(Locale.ROOT)).filter(t -> !t.isEmpty())
                .collect(Collectors.toSet());
    }

    private static String collectionLabel(Map<UUID, KnowledgeCollection> allowed, Set<UUID> requested) {
        if (requested.isEmpty()) {
            return "all";
        }
        return allowed.size() == 1 ? allowed.values().iterator().next().name() : "multiple";
    }

    private static RerankingService selectReranker(List<RerankingService> rerankers, RagProperties.Reranking config) {
        if (!config.enabled()) {
            return rerankers.stream().filter(r -> "none".equals(r.name())).findFirst()
                    .orElseGet(PassThroughRerankingService::new);
        }
        return rerankers.stream().filter(r -> r.name().equalsIgnoreCase(config.provider())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown rag.reranking.provider '" + config.provider()
                        + "'. Available: " + rerankers.stream().map(RerankingService::name).toList()));
    }

    private record Candidate(UUID chunkId, Double similarity, Double keywordScore, double score) {
    }
}
