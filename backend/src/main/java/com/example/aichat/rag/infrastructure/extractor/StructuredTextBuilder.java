package com.example.aichat.rag.infrastructure.extractor;

import com.example.aichat.rag.domain.DocumentExtractionException;
import com.example.aichat.rag.domain.ExtractedDocument.PageSpan;
import com.example.aichat.rag.domain.ExtractedDocument.SectionSpan;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds normalised plain text incrementally (single spaces, at most one blank line between blocks, no control
 * characters) while exposing stable offsets, so pages and sections can be recorded during extraction.
 */
final class StructuredTextBuilder {

    private final StringBuilder text = new StringBuilder();
    private final int maxCharacters;
    private final List<PageSpan> pages = new ArrayList<>();
    private final List<SectionSpan> sections = new ArrayList<>();
    private int pendingBreaks;
    private boolean pendingSpace;

    StructuredTextBuilder(int maxCharacters) {
        this.maxCharacters = maxCharacters;
    }

    void append(CharSequence raw) {
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\n' || c == '\r') {
                lineBreak();
            } else if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                pendingSpace = true;
            } else if (Character.isISOControl(c) || c == '\uFFFD' || Character.getType(c) == Character.FORMAT) {
                pendingSpace = pendingSpace || c == '\u00AD';
            } else {
                flush();
                text.append(c);
            }
        }
        if (text.length() > maxCharacters) {
            throw new DocumentExtractionException("The document text exceeds the limit of " + maxCharacters
                    + " characters (rag.ingestion.max-extracted-characters)");
        }
    }

    void lineBreak() {
        pendingBreaks = Math.max(pendingBreaks, 1);
        pendingSpace = false;
    }

    void paragraphBreak() {
        pendingBreaks = 2;
        pendingSpace = false;
    }

    void space() {
        pendingSpace = true;
    }

    /** Offset at which the next visible character will be written. */
    int nextOffset() {
        if (text.isEmpty()) {
            return 0;
        }
        return text.length() + (pendingBreaks > 0 ? pendingBreaks : pendingSpace ? 1 : 0);
    }

    int length() {
        return text.length();
    }

    void addPage(int pageNumber, int start) {
        int end = text.length();
        if (end > start) {
            pages.add(new PageSpan(pageNumber, start, end));
        } else if (!pages.isEmpty() || start == 0) {
            pages.add(new PageSpan(pageNumber, Math.min(start, end), end));
        }
    }

    void addSection(String title, int level, int start) {
        String clean = title == null ? "" : title.replaceAll("\\s+", " ").strip();
        if (!clean.isEmpty()) {
            sections.add(new SectionSpan(clean.length() > 300 ? clean.substring(0, 300) : clean, level, start));
        }
    }

    String text() {
        return text.toString();
    }

    List<PageSpan> pages() {
        return List.copyOf(pages);
    }

    List<SectionSpan> sections() {
        return List.copyOf(sections);
    }

    private void flush() {
        if (!text.isEmpty()) {
            if (pendingBreaks > 0) {
                text.append("\n".repeat(pendingBreaks));
            } else if (pendingSpace) {
                text.append(' ');
            }
        }
        pendingBreaks = 0;
        pendingSpace = false;
    }
}
