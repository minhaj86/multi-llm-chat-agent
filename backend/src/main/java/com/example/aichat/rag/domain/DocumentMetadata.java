package com.example.aichat.rag.domain;

import java.util.UUID;

/**
 * What is known about a document before its text is extracted.
 */
public record DocumentMetadata(UUID sourceId, UUID collectionId, String filename, String contentType, long size) {
}
