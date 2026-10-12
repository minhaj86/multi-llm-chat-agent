package com.example.aichat.rag.application;

import com.example.aichat.common.Times;
import com.example.aichat.rag.domain.DocumentExtractionException;
import com.example.aichat.rag.domain.DocumentMetadata;
import com.example.aichat.rag.domain.EmbeddingException;
import com.example.aichat.rag.domain.ExtractedDocument;
import com.example.aichat.rag.domain.IngestionStatus;
import com.example.aichat.rag.domain.KnowledgeChunk;
import com.example.aichat.rag.domain.KnowledgeChunkRepository;
import com.example.aichat.rag.domain.KnowledgeCollection;
import com.example.aichat.rag.domain.KnowledgeCollectionRepository;
import com.example.aichat.rag.domain.KnowledgeContentRepository;
import com.example.aichat.rag.domain.KnowledgeFileStore;
import com.example.aichat.rag.domain.KnowledgeSource;
import com.example.aichat.rag.domain.KnowledgeSourceRepository;
import com.example.aichat.rag.domain.KnowledgeSourceRepository.IndexingResult;
import com.example.aichat.rag.domain.VectorRecord;
import com.example.aichat.rag.domain.VectorStore;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * The indexing pipeline for one source: extract (or reuse stored text) -> chunk -> embed -> store. Status is
 * recorded at every stage so the admin UI can show progress.
 *
 * <p>The final write is a single transaction that locks the source row, removes the previous vectors and chunks
 * and writes the new ones. Old vectors therefore stay searchable until they are replaced, a reindex never leaves
 * duplicates, and a source deleted in the meantime is detected (no orphaned vectors). Re-running the pipeline is
 * always safe.
 */
@Service
public class IngestionPipeline {

    private static final Logger log = LoggerFactory.getLogger(IngestionPipeline.class);

    private final KnowledgeSourceRepository sources;
    private final KnowledgeCollectionRepository collections;
    private final KnowledgeContentRepository contents;
    private final KnowledgeFileStore files;
    private final KnowledgeChunkRepository chunks;
    private final VectorStore vectorStore;
    private final ExtractionService extraction;
    private final ChunkingService chunking;
    private final EmbeddingService embedding;
    private final RagMetrics metrics;
    private final TransactionOperations transactions;
    private final Clock clock;

    public IngestionPipeline(KnowledgeSourceRepository sources, KnowledgeCollectionRepository collections,
                             KnowledgeContentRepository contents, KnowledgeFileStore files,
                             KnowledgeChunkRepository chunks, VectorStore vectorStore, ExtractionService extraction,
                             ChunkingService chunking, EmbeddingService embedding, RagMetrics metrics,
                             TransactionOperations transactions, Clock clock) {
        this.sources = sources;
        this.collections = collections;
        this.contents = contents;
        this.files = files;
        this.chunks = chunks;
        this.vectorStore = vectorStore;
        this.extraction = extraction;
        this.chunking = chunking;
        this.embedding = embedding;
        this.metrics = metrics;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * Indexes the source.
     *
     * @param reextract parse the original file again even if extracted text is stored
     */
    public void process(UUID sourceId, boolean reextract) {
        Optional<KnowledgeSource> found = sources.findById(sourceId);
        if (found.isEmpty() || found.get().status() == IngestionStatus.DELETING) {
            return;
        }
        KnowledgeSource source = found.get();
        String collection = collections.findById(source.collectionId()).map(KnowledgeCollection::name).orElse(null);
        Map<String, Object> stats = new LinkedHashMap<>();
        long started = System.nanoTime();
        try {
            ExtractedDocument document = loadOrExtract(source, reextract, stats);

            transition(sourceId, IngestionStatus.CHUNKING);
            long chunkingStart = System.nanoTime();
            List<KnowledgeChunk> chunkList = chunking.chunk(document);
            stats.put("chunkingStrategy", chunking.strategyName());
            stats.put("chunkingMs", Times.elapsedMillis(chunkingStart));
            if (chunkList.isEmpty()) {
                throw new DocumentExtractionException("The document produced no chunks");
            }

            transition(sourceId, IngestionStatus.EMBEDDING);
            long embeddingStart = System.nanoTime();
            List<float[]> vectors = embedding.embedDocuments(chunkList.stream().map(KnowledgeChunk::content).toList());
            stats.put("embeddingMs", Times.elapsedMillis(embeddingStart));
            stats.put("embeddingProvider", embedding.providerId());
            stats.put("embeddingModel", embedding.modelId());
            stats.put("dimensions", vectors.getFirst().length);
            vectorStore.prepare(embedding.modelId(), vectors.getFirst().length);

            long storeStart = System.nanoTime();
            boolean stored = Boolean.TRUE.equals(transactions.execute(tx -> store(sourceId, chunkList, vectors,
                    document, stats, storeStart, started)));
            if (stored) {
                metrics.ingestion("success", collection, embedding.providerId(), embedding.modelId(),
                        System.nanoTime() - started);
                log.info("Indexed knowledge source {} chunks={} characters={} totalMs={}", sourceId,
                        chunkList.size(), document.text().length(), Times.elapsedMillis(started));
            } else {
                log.info("Knowledge source {} was deleted during indexing; results discarded", sourceId);
            }
        } catch (RuntimeException e) {
            String reason = describe(e);
            stats.put("failedAfterMs", Times.elapsedMillis(started));
            sources.markFailed(sourceId, reason, stats, Times.now(clock));
            metrics.ingestion("failed", collection, embedding.providerId(), embedding.modelId(),
                    System.nanoTime() - started);
            if (e instanceof DocumentExtractionException || e instanceof EmbeddingException) {
                log.warn("Indexing of knowledge source {} failed: {}", sourceId, reason);
            } else {
                log.error("Indexing of knowledge source {} failed unexpectedly", sourceId, e);
            }
        }
    }

    private ExtractedDocument loadOrExtract(KnowledgeSource source, boolean reextract, Map<String, Object> stats) {
        DocumentMetadata metadata = new DocumentMetadata(source.id(), source.collectionId(),
                source.originalFilename() == null ? source.title() : source.originalFilename(), source.mimeType(),
                source.fileSize());
        if (!reextract) {
            Optional<KnowledgeContentRepository.StoredContent> stored = contents.find(source.id());
            if (stored.isPresent()) {
                stats.put("extraction", "reused");
                return withMetadata(stored.get().document(), metadata);
            }
        }
        transition(source.id(), IngestionStatus.EXTRACTING);
        byte[] content = files.load(source.id()).orElseThrow(() -> new DocumentExtractionException(
                "The original content is no longer available; upload the document again"));
        long start = System.nanoTime();
        ExtractionService.Extraction result = extraction.extract(content, metadata);
        ExtractedDocument document = withMetadata(result.document(), metadata);
        contents.save(source.id(), document, Times.now(clock));
        stats.put("extraction", "extracted");
        stats.put("extractor", result.extractor());
        stats.put("extractionMs", Times.elapsedMillis(start));
        stats.put("characters", document.text().length());
        stats.put("pages", document.pages().size());
        stats.put("sections", document.sections().size());
        transition(source.id(), IngestionStatus.EXTRACTED);
        return document;
    }

    private Boolean store(UUID sourceId, List<KnowledgeChunk> drafted, List<float[]> vectors,
                          ExtractedDocument document, Map<String, Object> stats, long storeStart, long started) {
        Optional<IngestionStatus> status = sources.lockStatus(sourceId);
        if (status.isEmpty() || status.get() == IngestionStatus.DELETING) {
            return false;
        }
        KnowledgeSource current = sources.findById(sourceId).orElseThrow();
        List<KnowledgeChunk> finalChunks = new ArrayList<>(drafted.size());
        List<VectorRecord> records = new ArrayList<>(drafted.size());
        for (int i = 0; i < drafted.size(); i++) {
            KnowledgeChunk c = drafted.get(i);
            KnowledgeChunk chunk = new KnowledgeChunk(c.id(), c.sourceId(), current.collectionId(), c.chunkIndex(),
                    c.content(), c.tokenCount(), c.characterStart(), c.characterEnd(), c.pageNumber(),
                    c.sectionTitle(), c.metadata(), c.createdAt());
            finalChunks.add(chunk);
            records.add(new VectorRecord(chunk.id(), sourceId, current.collectionId(), embedding.modelId(),
                    vectors.get(i), current.enabled(), current.tags()));
        }
        vectorStore.deleteBySourceId(sourceId);
        chunks.replaceForSource(sourceId, finalChunks);
        vectorStore.store(records);
        stats.put("storeMs", Times.elapsedMillis(storeStart));
        stats.put("totalMs", Times.elapsedMillis(started));
        stats.put("vectorStore", vectorStore.id());
        sources.markIndexed(sourceId, new IndexingResult(embedding.providerId(), embedding.modelId(),
                finalChunks.size(), document.text().length(), stats), Times.now(clock));
        return true;
    }

    private void transition(UUID sourceId, IngestionStatus status) {
        sources.updateStatus(sourceId, status, null, Times.now(clock));
    }

    private static ExtractedDocument withMetadata(ExtractedDocument document, DocumentMetadata metadata) {
        return new ExtractedDocument(metadata, document.text(), document.pages(), document.sections(),
                document.properties());
    }

    static String describe(Throwable error) {
        if (error instanceof DocumentExtractionException || error instanceof EmbeddingException) {
            return error.getMessage();
        }
        String message = error.getMessage();
        return "Unexpected error (" + error.getClass().getSimpleName() + ")"
                + (message == null || message.isBlank() ? "" : ": " + (message.length() > 300
                ? message.substring(0, 300) : message));
    }
}
