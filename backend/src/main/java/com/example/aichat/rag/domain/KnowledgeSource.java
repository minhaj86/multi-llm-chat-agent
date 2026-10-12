package com.example.aichat.rag.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A document or manual text entry in the knowledge base, with its indexing state.
 *
 * <p>Versioning is prepared but intentionally simple: {@code version} counts uploads of the same document family
 * and {@code parentSourceId} points at the previous version.
 *
 * @param checksum       SHA-256 of the original bytes (files) or of the normalised text (manual entries)
 * @param characterCount length of the extracted text, once known
 * @param ingestionStats timings and counters of the last ingestion run (for the admin "Indexing" tab)
 */
public record KnowledgeSource(UUID id,
                              KnowledgeSourceType type,
                              String title,
                              String originalFilename,
                              String mimeType,
                              String sourceUrl,
                              UUID collectionId,
                              IngestionStatus status,
                              boolean enabled,
                              List<String> tags,
                              String checksum,
                              long fileSize,
                              Integer characterCount,
                              int version,
                              UUID parentSourceId,
                              UUID createdBy,
                              Instant createdAt,
                              Instant updatedAt,
                              Instant indexedAt,
                              String embeddingProvider,
                              String embeddingModel,
                              int chunkCount,
                              String errorMessage,
                              Map<String, Object> ingestionStats) {

    public static final int MAX_TITLE_LENGTH = 300;
    public static final int MAX_TAGS = 20;
    public static final int MAX_TAG_LENGTH = 50;

    public KnowledgeSource {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(collectionId, "collectionId");
        Objects.requireNonNull(status, "status");
        tags = tags == null ? List.of() : List.copyOf(tags);
        ingestionStats = ingestionStats == null ? Map.of() : Map.copyOf(ingestionStats);
    }

    /** Searchable right now: indexed, enabled, and not being deleted. */
    public boolean isSearchable() {
        return enabled && status == IngestionStatus.INDEXED;
    }

    /** Indexed with a different embedding model than the active one: vectors are not comparable any more. */
    public boolean isStale(String activeEmbeddingModel) {
        return status == IngestionStatus.INDEXED && embeddingModel != null
                && !embeddingModel.equals(activeEmbeddingModel);
    }
}
