package com.example.aichat.rag.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for knowledge sources. State changes are targeted updates (not whole-record saves) so that an
 * administrator editing metadata never races with a running ingestion job that updates the status.
 */
public interface KnowledgeSourceRepository {

    void insert(KnowledgeSource source);

    Optional<KnowledgeSource> findById(UUID id);

    Map<UUID, KnowledgeSource> findByIds(Collection<UUID> ids);

    PageResult<KnowledgeSource> search(KnowledgeSourceQuery query);

    List<KnowledgeSource> findByChecksum(String checksum);

    List<UUID> findIdsByStatus(Collection<IngestionStatus> statuses);

    /** Indexed sources whose vectors were produced by another embedding model than {@code activeModel}. */
    List<UUID> findStaleIds(String activeModel);

    boolean updateMetadata(UUID id, String title, UUID collectionId, List<String> tags, String sourceUrl, Instant now);

    boolean updateEnabled(UUID id, boolean enabled, Instant now);

    boolean updateStatus(UUID id, IngestionStatus status, String errorMessage, Instant now);

    /** Records the result of a successful ingestion run. */
    void markIndexed(UUID id, IndexingResult result, Instant now);

    /** Records a failed ingestion run. */
    void markFailed(UUID id, String errorMessage, Map<String, Object> stats, Instant now);

    /** New content for an existing source (replaced file, edited text): resets indexing state to UPLOADED. */
    void replaceContent(UUID id, ContentReplacement replacement, Instant now);

    /** Locks the source row for the current transaction and returns its status (empty if it no longer exists). */
    Optional<IngestionStatus> lockStatus(UUID id);

    void delete(UUID id);

    KnowledgeTotals totals();

    /** Source and chunk counts grouped by collection, status and embedding model (for metrics). */
    List<InventoryEntry> inventory();

    record InventoryEntry(UUID collectionId, IngestionStatus status, String embeddingModel, long sources,
                          long chunks) {
    }

    record IndexingResult(String embeddingProvider,
                          String embeddingModel,
                          int chunkCount,
                          int characterCount,
                          Map<String, Object> stats) {
    }

    record ContentReplacement(String title,
                              String originalFilename,
                              String mimeType,
                              String checksum,
                              long fileSize,
                              int version) {
    }

    record KnowledgeTotals(long sources,
                           long files,
                           long texts,
                           long indexed,
                           long failed,
                           long processing,
                           long disabled,
                           long chunks,
                           long bytes,
                           Map<String, Long> chunksByEmbeddingModel) {
    }
}
