package com.example.aichat.rag.application;

import com.example.aichat.rag.domain.IngestionStatus;
import com.example.aichat.rag.domain.KnowledgeSourceRepository;
import com.example.aichat.rag.domain.KnowledgeSourceRepository.InventoryEntry;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Housekeeping: after a restart, finishes interrupted deletions and resumes interrupted ingestion (safe because
 * the pipeline is idempotent); periodically refreshes the knowledge inventory gauges.
 */
@Component
public class KnowledgeMaintenance {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeMaintenance.class);

    private final KnowledgeSourceRepository sources;
    private final KnowledgeBaseService knowledgeBase;
    private final KnowledgeCollectionService collections;
    private final IngestionQueue queue;
    private final RagMetrics metrics;

    public KnowledgeMaintenance(KnowledgeSourceRepository sources, KnowledgeBaseService knowledgeBase,
                                KnowledgeCollectionService collections, IngestionQueue queue, RagMetrics metrics) {
        this.sources = sources;
        this.knowledgeBase = knowledgeBase;
        this.collections = collections;
        this.queue = queue;
        this.metrics = metrics;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        List<UUID> deleting = sources.findIdsByStatus(EnumSet.of(IngestionStatus.DELETING));
        deleting.forEach(knowledgeBase::purge);
        List<UUID> interrupted = sources.findIdsByStatus(EnumSet.of(IngestionStatus.UPLOADED,
                IngestionStatus.EXTRACTING, IngestionStatus.EXTRACTED, IngestionStatus.CHUNKING,
                IngestionStatus.EMBEDDING));
        interrupted.forEach(id -> queue.submit(id, false));
        if (!deleting.isEmpty() || !interrupted.isEmpty()) {
            log.info("Knowledge base recovery: completed {} deletion(s), resumed {} ingestion(s)", deleting.size(),
                    interrupted.size());
        }
        refreshInventory();
    }

    @Scheduled(fixedDelayString = "${rag.metrics-refresh-interval:PT1M}",
            initialDelayString = "${rag.metrics-refresh-interval:PT1M}")
    public void refreshInventory() {
        try {
            Map<UUID, String> names = new HashMap<>();
            collections.list().forEach(view -> names.put(view.collection().id(), view.collection().name()));
            List<RagMetrics.InventoryRow> documents = new ArrayList<>();
            Map<String, Long> chunkTotals = new HashMap<>();
            for (InventoryEntry entry : sources.inventory()) {
                String collection = names.getOrDefault(entry.collectionId(), "unknown");
                documents.add(new RagMetrics.InventoryRow(collection, entry.status().name(), entry.sources()));
                if (entry.status() == IngestionStatus.INDEXED) {
                    chunkTotals.merge(collection + '\u0000' + entry.embeddingModel(), entry.chunks(), Long::sum);
                }
            }
            List<RagMetrics.InventoryRow> chunks = chunkTotals.entrySet().stream().map(e -> {
                String[] parts = e.getKey().split("\u0000", 2);
                return new RagMetrics.InventoryRow(parts[0], parts[1], e.getValue());
            }).toList();
            metrics.inventory(documents, chunks);
        } catch (RuntimeException e) {
            log.debug("Knowledge inventory metrics not refreshed: {}", e.getMessage());
        }
    }
}
