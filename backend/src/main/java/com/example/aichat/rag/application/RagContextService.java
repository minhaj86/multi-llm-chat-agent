package com.example.aichat.rag.application;

import org.springframework.stereotype.Service;

/**
 * Question answering front of the RAG pipeline: retrieval (including optional reranking) followed by context
 * building. Used by chat and by the admin test console; it never talks to a chat model itself.
 */
@Service
public class RagContextService {

    private final RetrievalService retrieval;
    private final RagContextBuilder contextBuilder;

    public RagContextService(RetrievalService retrieval, RagContextBuilder contextBuilder) {
        this.retrieval = retrieval;
        this.contextBuilder = contextBuilder;
    }

    public RagPreparation prepare(String question, RetrievalOptions options) {
        RetrievalResult result = retrieval.search(question, options);
        return new RagPreparation(result, contextBuilder.build(question, result.chunks()));
    }

    public record RagPreparation(RetrievalResult retrieval, RagContext context) {
    }
}
