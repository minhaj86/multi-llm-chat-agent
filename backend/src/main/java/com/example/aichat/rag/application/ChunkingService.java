package com.example.aichat.rag.application;

import com.example.aichat.rag.domain.ChunkingStrategy;
import com.example.aichat.rag.domain.ExtractedDocument;
import com.example.aichat.rag.domain.KnowledgeChunk;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Applies the chunking strategy selected by {@code rag.chunking.strategy}. New strategies (semantic, parent-child,
 * table-aware, ...) are added as {@link ChunkingStrategy} beans.
 */
@Service
public class ChunkingService {

    private final ChunkingStrategy strategy;

    public ChunkingService(List<ChunkingStrategy> strategies, RagProperties properties) {
        String name = properties.chunking().strategy().strip().toLowerCase(Locale.ROOT);
        this.strategy = strategies.stream().filter(s -> s.name().equalsIgnoreCase(name)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown rag.chunking.strategy '" + name
                        + "'. Available: " + strategies.stream().map(ChunkingStrategy::name).toList()));
    }

    public String strategyName() {
        return strategy.name();
    }

    public List<KnowledgeChunk> chunk(ExtractedDocument document) {
        return strategy.chunk(document);
    }
}
