package com.example.aichat.application.chat;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * Runs fire-and-forget work (titles, summaries, finalisation after client disconnects) on virtual threads,
 * propagating the logging context (correlation id).
 */
@Component
public class BackgroundTasks implements DisposableBean {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public void execute(Runnable task) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        executor.execute(() -> withContext(context, () -> {
            task.run();
            return null;
        }));
    }

    public <T> CompletableFuture<T> submit(Supplier<T> task) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return CompletableFuture.supplyAsync(() -> withContext(context, task), executor);
    }

    @Override
    public void destroy() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }

    private static <T> T withContext(Map<String, String> context, Supplier<T> task) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            if (context == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(context);
            }
            return task.get();
        } finally {
            if (previous == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previous);
            }
        }
    }
}
