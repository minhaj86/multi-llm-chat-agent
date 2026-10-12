package com.example.aichat.rag.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Plain text of a document plus the structure needed for citations: page boundaries (PDF) and section headings
 * (DOCX, HTML, Markdown). Offsets are character positions in {@link #text()}.
 */
public record ExtractedDocument(DocumentMetadata metadata,
                                String text,
                                List<PageSpan> pages,
                                List<SectionSpan> sections,
                                Map<String, String> properties) {

    public ExtractedDocument {
        text = Objects.requireNonNull(text, "text");
        pages = pages == null ? List.of() : pages.stream().sorted(Comparator.comparingInt(PageSpan::start)).toList();
        sections = sections == null ? List.of()
                : sections.stream().sorted(Comparator.comparingInt(SectionSpan::start)).toList();
        properties = properties == null ? Map.of() : Map.copyOf(properties);
    }

    /** Page containing the given offset, or {@code null} when the format has no pages. */
    public Integer pageAt(int offset) {
        PageSpan found = null;
        for (PageSpan page : pages) {
            if (page.start() <= offset) {
                found = page;
            } else {
                break;
            }
        }
        return found == null ? null : found.pageNumber();
    }

    /** Title of the last section heading at or before the given offset. */
    public String sectionAt(int offset) {
        SectionSpan found = null;
        for (SectionSpan section : sections) {
            if (section.start() <= offset) {
                found = section;
            } else {
                break;
            }
        }
        return found == null ? null : found.title();
    }

    public record PageSpan(int pageNumber, int start, int end) {
    }

    public record SectionSpan(String title, int level, int start) {
    }
}
