package com.example.aichat.rag.domain;

/**
 * Ingestion lifecycle of a knowledge source.
 *
 * <pre>
 * UPLOADED -> EXTRACTING -> EXTRACTED -> CHUNKING -> EMBEDDING -> INDEXED
 *                      \______________________\_____________\-> FAILED
 * any -> DELETING (removal in progress)
 * </pre>
 */
public enum IngestionStatus {
    UPLOADED,
    EXTRACTING,
    EXTRACTED,
    CHUNKING,
    EMBEDDING,
    INDEXED,
    FAILED,
    DELETING;

    /** Queued or running: the source is not (yet) fully searchable in its current form. */
    public boolean isProcessing() {
        return this == UPLOADED || this == EXTRACTING || this == EXTRACTED || this == CHUNKING || this == EMBEDDING;
    }
}
