package com.example.aichat.rag.application;

import java.util.List;

/**
 * Turns retrieved chunks into model context. Implementations must respect the token and chunk budgets and must
 * keep retrieved text clearly separated from instructions (retrieved documents are untrusted).
 */
public interface RagContextBuilder {

    RagContext build(String question, List<RetrievedChunk> chunks);
}
