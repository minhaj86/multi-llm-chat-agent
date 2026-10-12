package com.example.aichat.rag.infrastructure.extractor;

import com.example.aichat.rag.domain.DocumentExtractionException;
import com.example.aichat.rag.domain.ExtractedDocument.SectionSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helpers for the plain-text formats: whitespace normalisation that keeps paragraphs, and Markdown headings.
 */
final class PlainText {

    private static final Pattern ATX_HEADING = Pattern.compile("^(#{1,6})[ \\t]+(.+?)[ \\t]*#*[ \\t]*$",
            Pattern.MULTILINE);
    private static final Pattern FENCE = Pattern.compile("^(```|~~~)", Pattern.MULTILINE);

    private PlainText() {
    }

    /** Removes control characters, trailing spaces and runs of blank lines (offsets refer to the result). */
    static String normalize(String text, int maxCharacters) {
        StringBuilder out = new StringBuilder(text.length());
        int blankLines = 0;
        for (String line : text.split("\n", -1)) {
            String clean = line.replace('\t', ' ').replaceAll("[\\p{Cc}&&[^\\n]]", "").stripTrailing();
            if (clean.isBlank()) {
                blankLines++;
                continue;
            }
            if (!out.isEmpty()) {
                out.append(blankLines > 0 ? "\n\n" : "\n");
            }
            out.append(clean);
            blankLines = 0;
            if (out.length() > maxCharacters) {
                throw new DocumentExtractionException("The document text exceeds the limit of " + maxCharacters
                        + " characters (rag.ingestion.max-extracted-characters)");
            }
        }
        return out.toString();
    }

    /** ATX headings ({@code # Title}) outside fenced code blocks. */
    static List<SectionSpan> markdownSections(String text) {
        List<int[]> fences = new ArrayList<>();
        Matcher fence = FENCE.matcher(text);
        int open = -1;
        while (fence.find()) {
            if (open < 0) {
                open = fence.start();
            } else {
                fences.add(new int[]{open, fence.end()});
                open = -1;
            }
        }
        if (open >= 0) {
            fences.add(new int[]{open, text.length()});
        }
        List<SectionSpan> sections = new ArrayList<>();
        Matcher heading = ATX_HEADING.matcher(text);
        while (heading.find()) {
            int start = heading.start();
            boolean inCode = fences.stream().anyMatch(range -> start > range[0] && start < range[1]);
            if (!inCode) {
                String title = heading.group(2).replaceAll("[*_`]", "").strip();
                if (!title.isEmpty()) {
                    sections.add(new SectionSpan(title.length() > 300 ? title.substring(0, 300) : title,
                            heading.group(1).length(), start));
                }
            }
        }
        return sections;
    }
}
