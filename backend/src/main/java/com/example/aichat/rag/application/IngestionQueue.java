package com.example.aichat.rag.application;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * Background ingestion: uploads return immediately with status {@code UPLOADED} and a bounded pool of workers
 * ({@code rag.ingestion.concurrency}) indexes documents. A source is never processed twice concurrently; a reindex
 * requested while it is running is executed once afterwards.
 */
@Component
public class IngestionQueue implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(IngestionQueue.class);

    private final IngestionPipeline pipeline;
    private final ExecutorService executor;
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pendingReextract = ConcurrentHashMap.newKeySet();

    public IngestionQueue(IngestionPipeline pipeline, RagProperties properties) {
        this.pipeline = pipeline;
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory threads = runnable -> {
            Thread thread = new Thread(runnable, "rag-ingest-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        this.executor = Executors.newFixedThreadPool(properties.ingestion().concurrency(), threads);
    }

    /** Schedules indexing of the source (after the current transaction has committed, as callers ensure). */
    public void submit(UUID sourceId, boolean reextract) {
        if (!running.add(sourceId)) {
            pending.add(sourceId);
            if (reextract) {
                pendingReextract.add(sourceId);
            }
            return;
        }
        executor.execute(() -> run(sourceId, reextract));
    }

    public boolean isActive(UUID sourceId) {
        return running.contains(sourceId);
    }

    private void run(UUID sourceId, boolean reextract) {
        boolean again;
        boolean force = reextract;
        try {
            do {
                pending.remove(sourceId);
                try {
                    pipeline.process(sourceId, force || pendingReextract.remove(sourceId));
                } catch (RuntimeException e) {
                    log.error("Ingestion worker failed for source {}", sourceId, e);
                }
                force = false;
                again = pending.remove(sourceId);
            } while (again);
        } finally {
            running.remove(sourceId);
        }
        if (pending.remove(sourceId)) {
            submit(sourceId, pendingReextract.remove(sourceId));
        }
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
