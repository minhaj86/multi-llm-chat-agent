package com.example.aichat.rag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.aichat.rag.domain.IngestionStatus;
import com.example.aichat.rag.domain.KeywordSearcher;
import com.example.aichat.rag.domain.KeywordSearcher.KeywordSearchResult;
import com.example.aichat.rag.domain.KnowledgeChunk;
import com.example.aichat.rag.domain.KnowledgeChunkRepository;
import com.example.aichat.rag.domain.KnowledgeCollection;
import com.example.aichat.rag.domain.KnowledgeSource;
import com.example.aichat.rag.domain.KnowledgeSourceRepository;
import com.example.aichat.rag.domain.KnowledgeSourceType;
import com.example.aichat.rag.domain.VectorSearchRequest;
import com.example.aichat.rag.domain.VectorSearchResult;
import com.example.aichat.rag.domain.VectorStore;
import com.example.aichat.support.RagTestProperties;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DefaultRetrievalServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");
    private final KnowledgeCollection engineering = new KnowledgeCollection(UUID.randomUUID(), "Engineering", null,
            true, false, NOW, NOW);
    private final KnowledgeCollection finance = new KnowledgeCollection(UUID.randomUUID(), "Finance", null, true,
            false, NOW, NOW);

    private final EmbeddingService embedding = mock(EmbeddingService.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final KeywordSearcher keywordSearcher = mock(KeywordSearcher.class);
    private final KnowledgeChunkRepository chunks = mock(KnowledgeChunkRepository.class);
    private final KnowledgeSourceRepository sources = mock(KnowledgeSourceRepository.class);
    private final KnowledgeCollectionService collections = mock(KnowledgeCollectionService.class);
    private final Map<UUID, KnowledgeChunk> chunkStore = new java.util.HashMap<>();
    private final Map<UUID, KnowledgeSource> sourceStore = new java.util.HashMap<>();

    @BeforeEach
    void setUp() {
        when(embedding.embedQuery(anyString())).thenReturn(new float[]{1f, 0f});
        when(embedding.modelId()).thenReturn("test-embedding");
        when(embedding.providerId()).thenReturn("test");
        when(collections.enabled()).thenReturn(List.of(engineering, finance));
        when(chunks.findByIds(any())).thenAnswer(inv -> ((Collection<UUID>) inv.getArgument(0)).stream()
                .map(chunkStore::get).filter(java.util.Objects::nonNull).toList());
        when(sources.findByIds(any())).thenAnswer(inv -> ((Collection<UUID>) inv.getArgument(0)).stream()
                .map(sourceStore::get).filter(java.util.Objects::nonNull)
                .collect(Collectors.toMap(KnowledgeSource::id, Function.identity())));
    }

    private DefaultRetrievalService service(RetrievalMode mode, double threshold, int topK) {
        return new DefaultRetrievalService(embedding, vectorStore, keywordSearcher, chunks, sources, collections,
                List.of(new PassThroughRerankingService()), RagTestProperties.with(RagTestProperties.chunking(800, 150),
                RagTestProperties.retrieval(topK, threshold, mode), new RagProperties.Context(6000, 8)),
                mock(RagMetrics.class));
    }

    private UUID chunk(KnowledgeCollection collection, boolean sourceEnabled, String title) {
        UUID sourceId = UUID.randomUUID();
        UUID chunkId = UUID.randomUUID();
        sourceStore.put(sourceId, new KnowledgeSource(sourceId, KnowledgeSourceType.TEXT, title, null,
                "text/markdown", null, collection.id(), IngestionStatus.INDEXED, sourceEnabled, List.of(), "x", 1,
                1, 1, null, null, NOW, NOW, NOW, "test", "test-embedding", 1, null, Map.of()));
        chunkStore.put(chunkId, new KnowledgeChunk(chunkId, sourceId, collection.id(), 0, title + " content", 5, 0,
                10, 3, "Intro", Map.of(), NOW));
        return chunkId;
    }

    private VectorSearchResult hit(UUID chunkId, double similarity) {
        return new VectorSearchResult(chunkId, chunkStore.get(chunkId).sourceId(), similarity);
    }

    @Test
    void dropsMatchesBelowTheSimilarityThreshold() {
        UUID good = chunk(engineering, true, "Good");
        UUID weak = chunk(engineering, true, "Weak");
        when(vectorStore.search(any())).thenReturn(List.of(hit(good, 0.82), hit(weak, 0.55)));

        RetrievalResult result = service(RetrievalMode.VECTOR, 0.7, 8).search("pricing", RetrievalOptions.defaults());

        assertThat(result.chunks()).extracting(RetrievedChunk::sourceTitle).containsExactly("Good");
        assertThat(result.chunks().getFirst().similarity()).isEqualTo(0.82);
        assertThat(result.chunks().getFirst().pageNumber()).isEqualTo(3);
        assertThat(result.candidates()).isEqualTo(2);
    }

    @Test
    void searchesOnlyRequestedEnabledCollectionsAndEnabledSources() {
        UUID visible = chunk(finance, true, "Visible");
        UUID disabledSource = chunk(finance, false, "Disabled");
        when(vectorStore.search(any())).thenReturn(List.of(hit(visible, 0.9), hit(disabledSource, 0.95)));

        RetrievalResult result = service(RetrievalMode.VECTOR, 0.5, 8)
                .search("budget", RetrievalOptions.forCollections(Set.of(finance.id())));

        ArgumentCaptor<VectorSearchRequest> request = ArgumentCaptor.forClass(VectorSearchRequest.class);
        verify(vectorStore).search(request.capture());
        assertThat(request.getValue().filter().collectionIds()).containsExactly(finance.id());
        assertThat(request.getValue().embeddingModel()).isEqualTo("test-embedding");
        assertThat(result.chunks()).extracting(RetrievedChunk::sourceTitle).containsExactly("Visible");
    }

    @Test
    void returnsNothingForDisabledOrUnknownCollections() {
        RetrievalResult result = service(RetrievalMode.VECTOR, 0.5, 8)
                .search("budget", RetrievalOptions.forCollections(Set.of(UUID.randomUUID())));

        assertThat(result.chunks()).isEmpty();
        verify(vectorStore, never()).search(any());
        verify(embedding, never()).embedQuery(anyString());
    }

    @Test
    void hybridModeFusesKeywordHits() {
        UUID semantic = chunk(engineering, true, "Semantic");
        UUID both = chunk(engineering, true, "Both");
        UUID keywordOnly = chunk(engineering, true, "Keyword");
        when(vectorStore.search(any())).thenReturn(List.of(hit(semantic, 0.9), hit(both, 0.8)));
        when(keywordSearcher.search(anyString(), any(), anyInt())).thenReturn(List.of(
                new KeywordSearchResult(keywordOnly, chunkStore.get(keywordOnly).sourceId(), 0.5),
                new KeywordSearchResult(both, chunkStore.get(both).sourceId(), 0.4)));

        RetrievalResult result = service(RetrievalMode.HYBRID, 0.5, 8).search("ERR-4711", RetrievalOptions.defaults());

        assertThat(result.chunks()).extracting(RetrievedChunk::sourceTitle)
                .containsExactly("Both", "Semantic", "Keyword");
        assertThat(result.chunks().getFirst().keywordScore()).isEqualTo(0.4);
        assertThat(result.chunks().getLast().similarity()).isNull();
    }

    @Test
    void cutsTheResultToTopK() {
        List<VectorSearchResult> hits = java.util.stream.IntStream.range(0, 6)
                .mapToObj(i -> hit(chunk(engineering, true, "Doc " + i), 0.9 - i * 0.01)).toList();
        when(vectorStore.search(any())).thenReturn(hits);

        RetrievalResult result = service(RetrievalMode.VECTOR, 0.5, 3).search("q", RetrievalOptions.defaults());

        assertThat(result.chunks()).hasSize(3);
        assertThat(result.topK()).isEqualTo(3);
    }
}
