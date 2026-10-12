package com.example.aichat.rag.application;

import com.example.aichat.application.common.ConflictException;
import com.example.aichat.application.common.ResourceNotFoundException;
import com.example.aichat.common.Times;
import com.example.aichat.domain.user.User;
import com.example.aichat.domain.user.UserRepository;
import com.example.aichat.rag.application.KnowledgeCollectionService.CollectionView;
import com.example.aichat.rag.application.KnowledgeSourceWriter.SourceMetadata;
import com.example.aichat.rag.domain.ExtractedDocument;
import com.example.aichat.rag.domain.IngestionStatus;
import com.example.aichat.rag.domain.KnowledgeChunk;
import com.example.aichat.rag.domain.KnowledgeChunkRepository;
import com.example.aichat.rag.domain.KnowledgeCollection;
import com.example.aichat.rag.domain.KnowledgeContentRepository;
import com.example.aichat.rag.domain.KnowledgeFileStore;
import com.example.aichat.rag.domain.KnowledgeSource;
import com.example.aichat.rag.domain.KnowledgeSourceQuery;
import com.example.aichat.rag.domain.KnowledgeSourceRepository;
import com.example.aichat.rag.domain.KnowledgeSourceRepository.KnowledgeTotals;
import com.example.aichat.rag.domain.PageResult;
import com.example.aichat.rag.domain.RagExecutionRepository;
import com.example.aichat.rag.domain.RagExecutionRepository.RetrievalStats;
import com.example.aichat.rag.domain.VectorStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Administration of the knowledge base: overview, inspection (extracted text, chunks, metadata, indexing state),
 * enabling, reindexing and deleting sources, and statistics.
 */
@Service
public class KnowledgeBaseService {

    private static final int DEFAULT_CONTENT_LIMIT = 2_000_000;

    private final KnowledgeSourceRepository sources;
    private final KnowledgeChunkRepository chunks;
    private final KnowledgeContentRepository contents;
    private final KnowledgeFileStore files;
    private final KnowledgeCollectionService collections;
    private final KnowledgeSourceWriter writer;
    private final VectorStore vectorStore;
    private final IngestionQueue queue;
    private final EmbeddingService embedding;
    private final ChunkingService chunking;
    private final RagExecutionRepository executions;
    private final UserRepository users;
    private final RagProperties properties;
    private final TransactionOperations transactions;
    private final Clock clock;

    public KnowledgeBaseService(KnowledgeSourceRepository sources, KnowledgeChunkRepository chunks,
                                KnowledgeContentRepository contents, KnowledgeFileStore files,
                                KnowledgeCollectionService collections, KnowledgeSourceWriter writer,
                                VectorStore vectorStore, IngestionQueue queue, EmbeddingService embedding,
                                ChunkingService chunking, RagExecutionRepository executions, UserRepository users,
                                RagProperties properties, TransactionOperations transactions, Clock clock) {
        this.sources = sources;
        this.chunks = chunks;
        this.contents = contents;
        this.files = files;
        this.collections = collections;
        this.writer = writer;
        this.vectorStore = vectorStore;
        this.queue = queue;
        this.embedding = embedding;
        this.chunking = chunking;
        this.executions = executions;
        this.users = users;
        this.properties = properties;
        this.transactions = transactions;
        this.clock = clock;
    }

    public PageResult<SourceSummary> list(KnowledgeSourceQuery query) {
        Map<UUID, String> names = collectionNames();
        String model = embedding.modelId();
        return sources.search(query).map(s -> new SourceSummary(s, names.get(s.collectionId()), typeLabel(s),
                s.isStale(model)));
    }

    public SourceDetails get(UUID id) {
        KnowledgeSource source = require(id);
        KnowledgeCollection collection = collections.get(source.collectionId());
        String createdBy = source.createdBy() == null ? null
                : users.findById(source.createdBy()).map(User::username).orElse(null);
        Integer tokens = source.characterCount() == null ? null
                : (int) Math.ceil(source.characterCount() / properties.chunking().charsPerToken());
        return new SourceDetails(source, collection.name(), createdBy, typeLabel(source),
                source.isStale(embedding.modelId()), vectorStore.countBySourceId(id), queue.isActive(id), tokens,
                vectorStore.id());
    }

    public void updateMetadata(UUID id, String title, UUID collectionId, List<String> tags, String sourceUrl) {
        KnowledgeSource source = require(id);
        writer.updateMetadata(id, new SourceMetadata(KnowledgeInputs.title(title, source.title()),
                collections.resolve(collectionId == null ? source.collectionId() : collectionId).id(),
                KnowledgeInputs.tags(tags), KnowledgeInputs.sourceUrl(sourceUrl)));
    }

    public void setEnabled(UUID id, boolean enabled) {
        require(id);
        writer.setEnabled(id, enabled);
    }

    /**
     * Schedules a new chunking/embedding run. Current vectors stay searchable until replaced.
     *
     * @param reextract parse the original file again (otherwise the stored text is reused when available)
     */
    public IngestionStatus reindex(UUID id, boolean reextract) {
        KnowledgeSource source = require(id);
        if (source.status() == IngestionStatus.DELETING) {
            throw new ConflictException("SOURCE_DELETING", "The knowledge source is being deleted");
        }
        boolean needsExtraction = reextract || contents.find(id).isEmpty();
        if (!queue.isActive(id)) {
            sources.updateStatus(id, IngestionStatus.UPLOADED, null, Times.now(clock));
        }
        queue.submit(id, needsExtraction);
        return IngestionStatus.UPLOADED;
    }

    /** Reindexes every source embedded with another model than the active one (after changing the model). */
    public int reindexStale() {
        List<UUID> stale = sources.findStaleIds(embedding.modelId());
        stale.forEach(id -> reindex(id, false));
        return stale.size();
    }

    /**
     * Deletes the source with its original, extracted text, chunks and vectors. The source is first marked
     * {@code DELETING} (running ingestion stops writing), then removed; if the process stops in between, the
     * deletion is completed at the next start.
     */
    public DeletionResult delete(UUID id) {
        KnowledgeSource source = require(id);
        long chunkCount = chunks.countBySourceId(id);
        long vectorCount = vectorStore.countBySourceId(id);
        transactions.executeWithoutResult(tx -> {
            sources.lockStatus(id).orElseThrow(() -> new ResourceNotFoundException("Knowledge source", id));
            sources.updateStatus(id, IngestionStatus.DELETING, null, Times.now(clock));
        });
        purge(id);
        return new DeletionResult(id, source.title(), chunkCount, vectorCount);
    }

    /** Removes everything that belongs to a source (idempotent). */
    public void purge(UUID id) {
        transactions.executeWithoutResult(tx -> {
            vectorStore.deleteBySourceId(id);
            chunks.deleteBySourceId(id);
            contents.delete(id);
            files.delete(id);
            sources.delete(id);
        });
    }

    public ContentView content(UUID id, Integer maxCharacters) {
        require(id);
        int limit = maxCharacters == null || maxCharacters <= 0 ? DEFAULT_CONTENT_LIMIT : maxCharacters;
        return contents.find(id).map(stored -> {
            ExtractedDocument document = stored.document();
            String text = document.text();
            boolean truncated = text.length() > limit;
            return new ContentView(id, true, truncated ? text.substring(0, limit) : text, text.length(),
                    (int) Math.ceil(text.length() / properties.chunking().charsPerToken()), truncated,
                    document.pages().size(), document.sections(), document.properties(), stored.extractedAt());
        }).orElse(new ContentView(id, false, "", 0, 0, false, 0, List.of(), Map.of(), null));
    }

    public PageResult<KnowledgeChunk> chunks(UUID id, int page, int size) {
        require(id);
        return chunks.findBySourceId(id, Math.max(0, page), Math.clamp(size, 1, 200));
    }

    public KnowledgeStats stats() {
        Instant now = clock.instant();
        KnowledgeTotals totals = sources.totals();
        List<CollectionView> collectionViews = collections.list();
        RetrievalStats lastDay = executions.statsSince(now.minus(Duration.ofDays(1)));
        RetrievalStats lastWeek = executions.statsSince(now.minus(Duration.ofDays(7)));
        int stale = sources.findStaleIds(embedding.modelId()).size();
        return new KnowledgeStats(properties.enabled(), totals, collectionViews, lastDay, lastWeek,
                new EmbeddingInfo(embedding.providerId(), embedding.modelId(), embedding.dimensions()),
                vectorStore.id(), stale, new SettingsInfo(chunking.strategyName(), properties.chunking().chunkSize(),
                properties.chunking().overlap(), properties.retrieval().mode().name(),
                properties.retrieval().topK(), properties.retrieval().similarityThreshold(),
                properties.context().maxTokens(), properties.reranking().enabled(),
                properties.ingestion().maxFileSize().toMegabytes(), DocumentType.supportedList()));
    }

    private KnowledgeSource require(UUID id) {
        return sources.findById(id).orElseThrow(() -> new ResourceNotFoundException("Knowledge source", id));
    }

    private Map<UUID, String> collectionNames() {
        return collections.list().stream().map(CollectionView::collection)
                .collect(Collectors.toMap(KnowledgeCollection::id, KnowledgeCollection::name, (a, b) -> a));
    }

    static String typeLabel(KnowledgeSource source) {
        return switch (source.type()) {
            case TEXT -> "Text";
            case URL -> "URL";
            case FILE -> DocumentType.labelFor(source.mimeType());
        };
    }

    public record SourceSummary(KnowledgeSource source, String collectionName, String typeLabel, boolean stale) {
    }

    public record SourceDetails(KnowledgeSource source,
                                String collectionName,
                                String createdByName,
                                String typeLabel,
                                boolean stale,
                                long vectorCount,
                                boolean workerActive,
                                Integer estimatedTokens,
                                String vectorStore) {
    }

    public record ContentView(UUID sourceId,
                              boolean available,
                              String text,
                              int characterCount,
                              int estimatedTokens,
                              boolean truncated,
                              int pageCount,
                              List<ExtractedDocument.SectionSpan> sections,
                              Map<String, String> properties,
                              Instant extractedAt) {
    }

    public record DeletionResult(UUID sourceId, String title, long chunks, long vectors) {
    }

    public record EmbeddingInfo(String provider, String model, int dimensions) {
    }

    public record SettingsInfo(String chunkingStrategy,
                               int chunkSize,
                               int overlap,
                               String retrievalMode,
                               int topK,
                               double similarityThreshold,
                               int contextMaxTokens,
                               boolean rerankingEnabled,
                               long maxFileSizeMb,
                               String supportedTypes) {
    }

    public record KnowledgeStats(boolean ragEnabled,
                                 KnowledgeTotals totals,
                                 List<CollectionView> collections,
                                 RetrievalStats lastDay,
                                 RetrievalStats lastWeek,
                                 EmbeddingInfo embedding,
                                 String vectorStore,
                                 int staleSources,
                                 SettingsInfo settings) {
    }
}
