package com.example.aichat.rag.application;

import java.util.UUID;

/**
 * Citation built from a chunk that was actually included in the model context; {@code index} is the number the
 * model was asked to use ({@code [1]}, {@code [2]}, ...).
 */
public record RagCitation(int index,
                          UUID sourceId,
                          UUID chunkId,
                          String title,
                          Integer pageNumber,
                          String sectionTitle,
                          String collectionName,
                          String sourceUrl,
                          int chunkIndex,
                          Double similarity,
                          double score) {
}
