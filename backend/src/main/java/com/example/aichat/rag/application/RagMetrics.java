package com.example.aichat.rag.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Micrometer instruments of the RAG subsystem (exported to Prometheus as {@code rag_*}). Labels are low
 * cardinality: provider, model, collection name and status; document text is never used as a label or logged.
 */
@Component
public class RagMetrics {

    private final MeterRegistry registry;
    private final MultiGauge documents;
    private final MultiGauge chunks;

    public RagMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.documents = MultiGauge.builder("rag.documents.total")
                .description("Knowledge sources by collection and status").register(registry);
        this.chunks = MultiGauge.builder("rag.chunks.total")
                .description("Indexed chunks by collection and embedding model").register(registry);
    }

    public void ingestion(String status, String collection, String embeddingProvider, String embeddingModel,
                          long durationNanos) {
        Tags tags = Tags.of("status", status, "collection", label(collection),
                "embedding_provider", label(embeddingProvider), "embedding_model", label(embeddingModel));
        Counter.builder("rag.ingestion").description("Ingestion runs").tags(tags).register(registry).increment();
        if ("failed".equals(status)) {
            Counter.builder("rag.ingestion.failed").description("Failed ingestion runs")
                    .tags("collection", label(collection)).register(registry).increment();
        }
        Timer.builder("rag.ingestion.duration").description("Duration of an ingestion run").tags(tags)
                .register(registry).record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void embedding(String provider, String model, String kind, String status, int inputs, long durationNanos) {
        Tags tags = Tags.of("embedding_provider", label(provider), "embedding_model", label(model), "kind", kind,
                "status", status);
        Counter.builder("rag.embedding.requests").description("Embedding requests").tags(tags).register(registry)
                .increment();
        Timer.builder("rag.embedding.duration").description("Embedding request duration").tags(tags)
                .register(registry).record(durationNanos, TimeUnit.NANOSECONDS);
        DistributionSummary.builder("rag.embedding.inputs").description("Texts per embedding request").tags(tags)
                .register(registry).record(inputs);
    }

    public void retrieval(String provider, String model, String collection, String status, int retrievedChunks,
                          long durationNanos) {
        Tags tags = Tags.of("embedding_provider", label(provider), "embedding_model", label(model),
                "collection", label(collection), "status", status);
        Counter.builder("rag.retrieval.requests").description("Retrieval requests").tags(tags).register(registry)
                .increment();
        Timer.builder("rag.retrieval.duration").description("Retrieval duration (embedding + search)").tags(tags)
                .register(registry).record(durationNanos, TimeUnit.NANOSECONDS);
        DistributionSummary.builder("rag.retrieved.chunks").description("Chunks returned by a retrieval")
                .tags(tags).register(registry).record(retrievedChunks);
    }

    /** Replaces the inventory gauges (refreshed periodically from the database). */
    public void inventory(List<InventoryRow> documentRows, List<InventoryRow> chunkRows) {
        documents.register(documentRows.stream().map(row -> MultiGauge.Row.of(Tags.of("collection",
                label(row.collection()), "status", label(row.qualifier())), row.value())).toList(), true);
        chunks.register(chunkRows.stream().map(row -> MultiGauge.Row.of(Tags.of("collection",
                label(row.collection()), "embedding_model", label(row.qualifier())), row.value())).toList(), true);
    }

    private static String label(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    public record InventoryRow(String collection, String qualifier, long value) {
    }
}
