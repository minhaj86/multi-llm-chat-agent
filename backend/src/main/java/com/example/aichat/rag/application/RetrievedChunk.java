package com.example.aichat.rag.application;

import com.example.aichat.rag.domain.KnowledgeSourceType;
import java.util.UUID;

/**
 * A chunk returned by retrieval with everything needed for context building and citations.
 *
 * @param similarity   cosine similarity of the vector match ({@code null} for keyword-only hits)
 * @param keywordScore full-text rank in hybrid mode
 * @param rerankScore  score assigned by a reranker, when one is active
 * @param score        final ranking score (similarity, fused score or rerank score)
 */
public record RetrievedChunk(UUID chunkId,
                             UUID sourceId,
                             String sourceTitle,
                             KnowledgeSourceType sourceType,
                             String sourceUrl,
                             UUID collectionId,
                             String collectionName,
                             int chunkIndex,
                             String content,
                             int tokenCount,
                             Integer pageNumber,
                             String sectionTitle,
                             Double similarity,
                             Double keywordScore,
                             Double rerankScore,
                             double score) {

    public RetrievedChunk withRerankScore(double value) {
        return new RetrievedChunk(chunkId, sourceId, sourceTitle, sourceType, sourceUrl, collectionId,
                collectionName, chunkIndex, content, tokenCount, pageNumber, sectionTitle, similarity, keywordScore,
                value, value);
    }
}
