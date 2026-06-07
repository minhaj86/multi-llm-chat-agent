package com.example.aichat.infrastructure.llm.compatible;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aichat.domain.llm.LlmChunk;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ThinkTagParserTest {

    private static String text(List<LlmChunk> chunks) {
        StringBuilder out = new StringBuilder();
        chunks.stream().filter(LlmChunk.TextDelta.class::isInstance)
                .forEach(c -> out.append(((LlmChunk.TextDelta) c).text()));
        return out.toString();
    }

    private static String reasoning(List<LlmChunk> chunks) {
        StringBuilder out = new StringBuilder();
        chunks.stream().filter(LlmChunk.ReasoningDelta.class::isInstance)
                .forEach(c -> out.append(((LlmChunk.ReasoningDelta) c).text()));
        return out.toString();
    }

    private static List<LlmChunk> feed(String... parts) {
        ThinkTagParser parser = new ThinkTagParser();
        List<LlmChunk> chunks = new ArrayList<>();
        for (String part : parts) {
            chunks.addAll(parser.accept(part));
        }
        chunks.addAll(parser.flush());
        return chunks;
    }

    @Test
    void separatesLeadingThinkBlockSplitAcrossChunks() {
        List<LlmChunk> chunks = feed("<thi", "nk>\nLet me ", "think.</th", "ink>\n\nThe answer", " is 42.");

        assertThat(reasoning(chunks)).isEqualTo("\nLet me think.");
        assertThat(text(chunks)).isEqualTo("The answer is 42.");
    }

    @Test
    void contentWithoutThinkBlockIsPassedThroughUnchanged() {
        List<LlmChunk> chunks = feed("Hello", " <b>world</b>");

        assertThat(reasoning(chunks)).isEmpty();
        assertThat(text(chunks)).isEqualTo("Hello <b>world</b>");
    }

    @Test
    void thinkTagInTheMiddleOfAnAnswerIsNotTreatedAsReasoning() {
        List<LlmChunk> chunks = feed("Use the <think> tag like this: <think>x</think>");

        assertThat(reasoning(chunks)).isEmpty();
        assertThat(text(chunks)).isEqualTo("Use the <think> tag like this: <think>x</think>");
    }

    @Test
    void emptyThinkBlockFromDisabledReasoningProducesOnlyContent() {
        List<LlmChunk> chunks = feed("<think>\n\n</think>\n\n", "Paris");

        assertThat(reasoning(chunks)).isBlank();
        assertThat(text(chunks)).isEqualTo("Paris");
    }

    @Test
    void unterminatedThinkBlockIsFlushedAsReasoning() {
        List<LlmChunk> chunks = feed("<think>still thinking when the limit was hit");

        assertThat(reasoning(chunks)).isEqualTo("still thinking when the limit was hit");
        assertThat(text(chunks)).isEmpty();
    }

    @Test
    void splitsCompleteAnswers() {
        ThinkTagParser.Split split = ThinkTagParser.split("<think>plan</think>\nAnswer");

        assertThat(split.reasoning()).isEqualTo("plan");
        assertThat(split.content()).isEqualTo("Answer");
    }
}
