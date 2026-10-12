package com.example.aichat.rag.application;

import com.example.aichat.application.chat.context.TokenEstimator;
import com.example.aichat.rag.domain.ChunkingStrategy;
import com.example.aichat.rag.domain.ExtractedDocument;
import com.example.aichat.rag.domain.KnowledgeChunk;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Recursive splitting in the spirit of a "recursive character text splitter": the text is cut at the coarsest
 * boundary that keeps pieces under the chunk size (paragraphs, then lines, sentences, clauses, words, and finally
 * characters); pieces are merged greedily up to {@code rag.chunking.chunk-size} with {@code overlap} carried into
 * the next chunk. All work happens on character ranges of the original text, so every chunk keeps exact offsets
 * and the page and section where it starts.
 */
@Component
public class RecursiveTextChunkingStrategy implements ChunkingStrategy {

    private static final List<String> SEPARATORS = List.of("\n\n", "\n", ". ", "? ", "! ", "; ", ": ", ", ", " ");

    private final RagProperties properties;
    private final TokenEstimator tokenEstimator;
    private final Clock clock;

    public RecursiveTextChunkingStrategy(RagProperties properties, TokenEstimator tokenEstimator, Clock clock) {
        this.properties = properties;
        this.tokenEstimator = tokenEstimator;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "recursive";
    }

    @Override
    public List<KnowledgeChunk> chunk(ExtractedDocument document) {
        Objects.requireNonNull(document.metadata(), "document metadata");
        String text = document.text();
        int maxChars = properties.chunking().chunkSizeCharacters();
        int overlapChars = properties.chunking().overlapCharacters();
        List<int[]> pieces = new ArrayList<>();
        split(text, 0, text.length(), 0, maxChars, pieces);
        List<int[]> ranges = merge(text, pieces, maxChars, overlapChars);

        Instant now = clock.instant();
        List<KnowledgeChunk> chunks = new ArrayList<>(ranges.size());
        for (int[] range : ranges) {
            int start = range[0];
            int end = range[1];
            while (start < end && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
            while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
                end--;
            }
            if (start >= end) {
                continue;
            }
            String content = text.substring(start, end);
            chunks.add(new KnowledgeChunk(UUID.randomUUID(), document.metadata().sourceId(),
                    document.metadata().collectionId(), chunks.size(), content, tokenEstimator.estimate(content),
                    start, end, document.pageAt(start), document.sectionAt(start), metadata(document, start, end),
                    now));
        }
        return chunks;
    }

    /** Recursively cuts {@code [from, to)} into pieces of at most {@code max} characters. */
    static void split(String text, int from, int to, int level, int max, List<int[]> out) {
        if (to - from <= max) {
            if (to > from) {
                out.add(new int[]{from, to});
            }
            return;
        }
        for (int l = level; l < SEPARATORS.size(); l++) {
            String separator = SEPARATORS.get(l);
            List<int[]> parts = splitOn(text, from, to, separator);
            if (parts.size() > 1) {
                for (int[] part : parts) {
                    split(text, part[0], part[1], l + 1, max, out);
                }
                return;
            }
        }
        for (int start = from; start < to; start += max) {
            out.add(new int[]{start, Math.min(to, start + max)});
        }
    }

    /** Splits after each occurrence of the separator; separators stay attached to the preceding part. */
    private static List<int[]> splitOn(String text, int from, int to, String separator) {
        List<int[]> parts = new ArrayList<>();
        int start = from;
        int index = text.indexOf(separator, from);
        while (index >= 0 && index + separator.length() <= to) {
            int end = index + separator.length();
            if (end > start) {
                parts.add(new int[]{start, end});
            }
            start = end;
            index = text.indexOf(separator, end);
        }
        if (start < to) {
            parts.add(new int[]{start, to});
        }
        return parts;
    }

    /**
     * Greedy merge of contiguous pieces with overlap. Whole trailing pieces are carried over when they fit into the
     * overlap; otherwise the tail of the previous chunk (starting at a word boundary) is carried over, so
     * consecutive chunks overlap even when every paragraph is longer than the overlap.
     */
    static List<int[]> merge(String text, List<int[]> pieces, int maxChars, int overlapChars) {
        List<int[]> chunks = new ArrayList<>();
        Deque<int[]> window = new ArrayDeque<>();
        int length = 0;
        for (int[] piece : pieces) {
            int size = piece[1] - piece[0];
            if (!window.isEmpty() && length + size > maxChars) {
                int chunkEnd = window.peekLast()[1];
                chunks.add(new int[]{window.peekFirst()[0], chunkEnd});
                while (!window.isEmpty() && (length > overlapChars || length + size > maxChars)) {
                    int[] removed = window.pollFirst();
                    length -= removed[1] - removed[0];
                }
                if (window.isEmpty() && overlapChars > 0) {
                    int start = wordBoundaryAfter(text, chunkEnd - overlapChars, chunkEnd);
                    if (start < chunkEnd && chunkEnd - start + size <= maxChars) {
                        window.addLast(new int[]{start, chunkEnd});
                        length = chunkEnd - start;
                    }
                }
            }
            window.addLast(piece);
            length += size;
        }
        if (!window.isEmpty()) {
            int[] last = {window.peekFirst()[0], window.peekLast()[1]};
            boolean coveredByPrevious = !chunks.isEmpty() && chunks.getLast()[1] >= last[1];
            if (!coveredByPrevious) {
                chunks.add(last);
            }
        }
        return chunks;
    }

    /** First position at or after {@code from} that starts a word (never past {@code limit}). */
    private static int wordBoundaryAfter(String text, int from, int limit) {
        int position = Math.max(0, from);
        if (position == 0 || Character.isWhitespace(text.charAt(position - 1))) {
            return position;
        }
        while (position < limit && !Character.isWhitespace(text.charAt(position))) {
            position++;
        }
        while (position < limit && Character.isWhitespace(text.charAt(position))) {
            position++;
        }
        return position;
    }

    private static Map<String, Object> metadata(ExtractedDocument document, int start, int end) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        Integer firstPage = document.pageAt(start);
        Integer lastPage = document.pageAt(Math.max(start, end - 1));
        if (firstPage != null && lastPage != null && !firstPage.equals(lastPage)) {
            metadata.put("pageEnd", lastPage);
        }
        Set<String> sections = new LinkedHashSet<>();
        document.sections().stream().filter(s -> s.start() > start && s.start() < end)
                .forEach(s -> sections.add(s.title()));
        if (!sections.isEmpty()) {
            metadata.put("sections", List.copyOf(sections));
        }
        return metadata;
    }
}
