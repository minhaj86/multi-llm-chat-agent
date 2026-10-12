package com.example.aichat.rag.domain;

import java.util.UUID;

/**
 * A chunk returned by one retrieval, with its scores. Title, page and section are copied so that citations stay
 * readable after the source is reindexed or deleted ({@code chunkId}/{@code sourceId} then become {@code null}).
 *
 * @param citationIndex number used in the answer ({@code [1]}) when the chunk was sent to the LLM, else null
 */
public record RagRetrievedChunk(int rank,
                                UUID chunkId,
                                UUID sourceId,
                                String sourceTitle,
                                String collectionName,
                                Integer chunkIndex,
                                Integer pageNumber,
                                String sectionTitle,
                                Double similarityScore,
                                Double keywordScore,
                                Double rerankerScore,
                                Integer citationIndex) {
}
