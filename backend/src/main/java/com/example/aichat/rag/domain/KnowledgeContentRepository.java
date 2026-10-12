package com.example.aichat.rag.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores the extracted text of a source with its structure, so that reindexing (new chunking settings or a new
 * embedding model) does not need to parse the original document again.
 */
public interface KnowledgeContentRepository {

    void save(UUID sourceId, ExtractedDocument document, Instant extractedAt);

    Optional<StoredContent> find(UUID sourceId);

    void delete(UUID sourceId);

    record StoredContent(ExtractedDocument document, Instant extractedAt) {
    }
}
