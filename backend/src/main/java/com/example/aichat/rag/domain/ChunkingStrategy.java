package com.example.aichat.rag.domain;

import java.util.List;

/**
 * Splits an extracted document into retrievable chunks. Chunks must carry character offsets into the document
 * text, and page/section metadata when the document has them, so that citations stay traceable.
 */
public interface ChunkingStrategy {

    /** Name used in configuration ({@code rag.chunking.strategy}), e.g. {@code recursive}. */
    String name();

    List<KnowledgeChunk> chunk(ExtractedDocument document);
}
