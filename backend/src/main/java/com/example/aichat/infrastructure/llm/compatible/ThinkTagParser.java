package com.example.aichat.infrastructure.llm.compatible;

import com.example.aichat.domain.llm.LlmChunk;
import java.util.ArrayList;
import java.util.List;

/**
 * Incremental parser that separates a leading {@code <think>...</think>} block (emitted inline by reasoning models
 * such as Qwen3 or DeepSeek-R1 on servers without a reasoning parser) from the answer. Tags may be split across
 * streamed chunks. Only a block at the very beginning of the output is treated as reasoning, so answers that merely
 * mention the tag are left untouched.
 */
public final class ThinkTagParser {

    static final String OPEN = "<think>";
    static final String CLOSE = "</think>";

    private enum State { START, THINKING, CONTENT }

    private final StringBuilder pending = new StringBuilder();
    private State state = State.START;
    private boolean skipLeadingWhitespace;

    public List<LlmChunk> accept(String text) {
        List<LlmChunk> out = new ArrayList<>(2);
        if (text == null || text.isEmpty()) {
            return out;
        }
        pending.append(text);
        process(out);
        return out;
    }

    /** Emits whatever is buffered at the end of the stream. */
    public List<LlmChunk> flush() {
        List<LlmChunk> out = new ArrayList<>(1);
        if (!pending.isEmpty()) {
            String rest = pending.toString();
            pending.setLength(0);
            if (state == State.THINKING) {
                out.add(LlmChunk.reasoning(rest));
            } else if (!(skipLeadingWhitespace && rest.isBlank())) {
                out.add(LlmChunk.text(skipLeadingWhitespace ? rest.stripLeading() : rest));
            }
        }
        return out;
    }

    /** Splits a complete (non-streamed) answer into reasoning and content. */
    public static Split split(String text) {
        ThinkTagParser parser = new ThinkTagParser();
        List<LlmChunk> chunks = new ArrayList<>(parser.accept(text));
        chunks.addAll(parser.flush());
        StringBuilder reasoning = new StringBuilder();
        StringBuilder content = new StringBuilder();
        for (LlmChunk chunk : chunks) {
            if (chunk instanceof LlmChunk.ReasoningDelta r) {
                reasoning.append(r.text());
            } else if (chunk instanceof LlmChunk.TextDelta t) {
                content.append(t.text());
            }
        }
        return new Split(reasoning.isEmpty() ? null : reasoning.toString().strip(), content.toString());
    }

    private void process(List<LlmChunk> out) {
        while (!pending.isEmpty()) {
            switch (state) {
                case START -> {
                    String buffered = pending.toString();
                    String trimmed = buffered.stripLeading();
                    if (trimmed.isEmpty() || (OPEN.startsWith(trimmed) && trimmed.length() < OPEN.length())) {
                        return;
                    }
                    if (trimmed.startsWith(OPEN)) {
                        pending.setLength(0);
                        pending.append(trimmed.substring(OPEN.length()));
                        state = State.THINKING;
                    } else {
                        state = State.CONTENT;
                    }
                }
                case THINKING -> {
                    String buffered = pending.toString();
                    int close = buffered.indexOf(CLOSE);
                    if (close >= 0) {
                        if (close > 0) {
                            out.add(LlmChunk.reasoning(buffered.substring(0, close)));
                        }
                        pending.setLength(0);
                        pending.append(buffered.substring(close + CLOSE.length()));
                        state = State.CONTENT;
                        skipLeadingWhitespace = true;
                    } else {
                        int keep = partialSuffix(buffered, CLOSE);
                        String emit = buffered.substring(0, buffered.length() - keep);
                        if (!emit.isEmpty()) {
                            out.add(LlmChunk.reasoning(emit));
                        }
                        pending.setLength(0);
                        pending.append(buffered.substring(buffered.length() - keep));
                        return;
                    }
                }
                case CONTENT -> {
                    String buffered = pending.toString();
                    pending.setLength(0);
                    if (skipLeadingWhitespace) {
                        buffered = buffered.stripLeading();
                        if (buffered.isEmpty()) {
                            return;
                        }
                        skipLeadingWhitespace = false;
                    }
                    out.add(LlmChunk.text(buffered));
                    return;
                }
            }
        }
    }

    /** Length of the longest suffix of {@code text} that is a proper prefix of {@code token}. */
    private static int partialSuffix(String text, String token) {
        int max = Math.min(text.length(), token.length() - 1);
        for (int length = max; length > 0; length--) {
            if (token.startsWith(text.substring(text.length() - length))) {
                return length;
            }
        }
        return 0;
    }

    public record Split(String reasoning, String content) {
    }
}
