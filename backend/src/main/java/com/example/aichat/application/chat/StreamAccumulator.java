package com.example.aichat.application.chat;

import com.example.aichat.domain.llm.FinishReason;
import com.example.aichat.domain.llm.LlmChunk;
import com.example.aichat.domain.llm.LlmUsage;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Accumulates a streamed answer and its timing. Thread-safe: chunks arrive on I/O threads while cancellation may
 * finalise the generation from another thread.
 */
public final class StreamAccumulator {

    private final long startNanos = System.nanoTime();
    private final StringBuilder content = new StringBuilder();
    private final StringBuilder reasoning = new StringBuilder();
    private final AtomicBoolean finished = new AtomicBoolean();
    private volatile long firstOutputNanos;
    private volatile LlmChunk.Completed completion;

    public synchronized void accept(LlmChunk chunk) {
        switch (chunk) {
            case LlmChunk.TextDelta delta -> {
                markFirstOutput(delta.text());
                content.append(delta.text());
            }
            case LlmChunk.ReasoningDelta delta -> {
                markFirstOutput(delta.text());
                reasoning.append(delta.text());
            }
            case LlmChunk.Completed done -> completion = done;
        }
    }

    public synchronized String content() {
        return content.toString();
    }

    public synchronized String reasoning() {
        return reasoning.isEmpty() ? null : reasoning.toString();
    }

    public LlmChunk.Completed completion() {
        return completion;
    }

    public LlmUsage usage() {
        LlmChunk.Completed done = completion;
        return done == null ? LlmUsage.UNKNOWN : done.usage();
    }

    public FinishReason finishReason() {
        LlmChunk.Completed done = completion;
        return done == null ? FinishReason.UNKNOWN : done.finishReason();
    }

    public long latencyMs() {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000L);
    }

    public Long timeToFirstTokenMs() {
        long first = firstOutputNanos;
        return first == 0 ? null : Math.max(0, (first - startNanos) / 1_000_000L);
    }

    /** Returns {@code true} exactly once - used to finalise a generation a single time. */
    public boolean markFinished() {
        return finished.compareAndSet(false, true);
    }

    private void markFirstOutput(String text) {
        if (firstOutputNanos == 0 && text != null && !text.isEmpty()) {
            firstOutputNanos = System.nanoTime();
        }
    }
}
