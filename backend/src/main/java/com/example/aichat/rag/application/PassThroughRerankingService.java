package com.example.aichat.rag.application;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Default reranker: keeps the retrieval order.
 */
@Component
public class PassThroughRerankingService implements RerankingService {

    @Override
    public String name() {
        return "none";
    }

    @Override
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> chunks) {
        return chunks;
    }
}
