package com.example.aichat.application.chat.knowledge;

import java.util.UUID;

/**
 * A source reference of an answer. Built from the chunks that were actually sent to the model (never from model
 * output), so {@code index} matches the bracketed numbers in the answer.
 *
 * @param chunkId  {@code null} when the chunk no longer exists (source deleted or reindexed)
 * @param sourceId {@code null} when the source was deleted
 */
public record KnowledgeCitation(int index,
                                UUID sourceId,
                                UUID chunkId,
                                String title,
                                Integer pageNumber,
                                String sectionTitle,
                                String collectionName,
                                Double score) {
}
