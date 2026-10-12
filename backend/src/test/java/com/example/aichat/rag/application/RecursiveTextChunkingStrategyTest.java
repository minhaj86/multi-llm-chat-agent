package com.example.aichat.rag.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aichat.application.chat.context.HeuristicTokenEstimator;
import com.example.aichat.rag.domain.DocumentMetadata;
import com.example.aichat.rag.domain.ExtractedDocument;
import com.example.aichat.rag.domain.ExtractedDocument.PageSpan;
import com.example.aichat.rag.domain.ExtractedDocument.SectionSpan;
import com.example.aichat.rag.domain.KnowledgeChunk;
import com.example.aichat.support.RagTestProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RecursiveTextChunkingStrategyTest {

    private static final UUID SOURCE = UUID.randomUUID();
    private static final UUID COLLECTION = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T10:00:00Z"), ZoneOffset.UTC);

    private static RecursiveTextChunkingStrategy strategy(int chunkTokens, int overlapTokens) {
        return new RecursiveTextChunkingStrategy(RagTestProperties.with(
                RagTestProperties.chunking(chunkTokens, overlapTokens), RagTestProperties.retrieval(8, 0.7,
                        RetrievalMode.VECTOR), new RagProperties.Context(6000, 8)),
                new HeuristicTokenEstimator(3.5), CLOCK);
    }

    private static ExtractedDocument document(String text, List<PageSpan> pages, List<SectionSpan> sections) {
        return new ExtractedDocument(new DocumentMetadata(SOURCE, COLLECTION, "doc.txt", "text/plain", text.length()),
                text, pages, sections, Map.of());
    }

    private static String paragraphs(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> "Paragraph " + i + " explains how pricing rule " + i
                        + " converts currencies for market " + i + ". It also covers rounding and taxes.")
                .collect(Collectors.joining("\n\n"));
    }

    @Test
    void chunksRespectTheSizeAndKeepExactOffsets() {
        String text = paragraphs(40);
        List<KnowledgeChunk> chunks = strategy(100, 20).chunk(document(text, List.of(), List.of()));

        int maxChars = (int) Math.round(100 * 3.5);
        assertThat(chunks).hasSizeGreaterThan(5);
        for (int i = 0; i < chunks.size(); i++) {
            KnowledgeChunk chunk = chunks.get(i);
            assertThat(chunk.chunkIndex()).isEqualTo(i);
            assertThat(chunk.content().length()).isLessThanOrEqualTo(maxChars);
            assertThat(text.substring(chunk.characterStart(), chunk.characterEnd())).isEqualTo(chunk.content());
            assertThat(chunk.content()).isEqualTo(chunk.content().strip()).isNotBlank();
            assertThat(chunk.tokenCount()).isPositive();
            assertThat(chunk.sourceId()).isEqualTo(SOURCE);
            assertThat(chunk.collectionId()).isEqualTo(COLLECTION);
        }
        assertThat(chunks.getLast().characterEnd()).isEqualTo(text.length());
    }

    @Test
    void consecutiveChunksOverlapAndCoverTheWholeText() {
        String text = paragraphs(30);
        List<KnowledgeChunk> chunks = strategy(100, 30).chunk(document(text, List.of(), List.of()));

        assertThat(chunks.getFirst().characterStart()).isZero();
        for (int i = 1; i < chunks.size(); i++) {
            KnowledgeChunk previous = chunks.get(i - 1);
            KnowledgeChunk current = chunks.get(i);
            assertThat(current.characterStart()).as("chunk %d overlaps the previous one", i)
                    .isLessThan(previous.characterEnd());
            assertThat(current.characterEnd()).isGreaterThan(previous.characterEnd());
        }
    }

    @Test
    void splitsAtParagraphBoundariesBeforeSentences() {
        String first = "First paragraph about currency conversion. " + "It explains the daily exchange rates. "
                .repeat(2).strip();
        String second = "Second paragraph about tax rules. " + "It explains value added tax per market. "
                .repeat(2).strip();
        String text = first + "\n\n" + second;
        List<KnowledgeChunk> chunks = strategy(50, 0).chunk(document(text, List.of(), List.of()));

        assertThat(chunks).extracting(KnowledgeChunk::content).containsExactly(first, second);
    }

    @Test
    void recordsPageAndSectionOfEachChunk() {
        String page1 = "Introduction\n\n" + paragraphs(3);
        String page2 = "Architecture\n\n" + paragraphs(3);
        String text = page1 + "\n\n" + page2;
        int page2Start = page1.length() + 2;
        List<KnowledgeChunk> chunks = strategy(60, 0).chunk(document(text,
                List.of(new PageSpan(1, 0, page1.length()), new PageSpan(2, page2Start, text.length())),
                List.of(new SectionSpan("Introduction", 1, 0), new SectionSpan("Architecture", 1, page2Start))));

        assertThat(chunks.getFirst().pageNumber()).isEqualTo(1);
        assertThat(chunks.getFirst().sectionTitle()).isEqualTo("Introduction");
        assertThat(chunks.getLast().pageNumber()).isEqualTo(2);
        assertThat(chunks.getLast().sectionTitle()).isEqualTo("Architecture");
    }

    @Test
    void hardSplitsTextWithoutSeparators() {
        String text = "x".repeat(1000);
        List<KnowledgeChunk> chunks = strategy(50, 0).chunk(document(text, List.of(), List.of()));

        int maxChars = (int) Math.round(50 * 3.5);
        assertThat(chunks).hasSize((int) Math.ceil(1000.0 / maxChars));
        assertThat(chunks).allSatisfy(c -> assertThat(c.content().length()).isLessThanOrEqualTo(maxChars));
    }

    @Test
    void shortDocumentsBecomeASingleChunk() {
        List<KnowledgeChunk> chunks = strategy(800, 150).chunk(document("  Short note.  ", List.of(), List.of()));

        assertThat(chunks).singleElement().satisfies(chunk -> {
            assertThat(chunk.content()).isEqualTo("Short note.");
            assertThat(chunk.characterStart()).isEqualTo(2);
        });
    }
}
