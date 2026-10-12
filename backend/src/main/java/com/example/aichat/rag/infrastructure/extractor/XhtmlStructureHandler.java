package com.example.aichat.rag.infrastructure.extractor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Set;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Turns Tika's XHTML output into structured plain text: block elements become line/paragraph breaks, table cells
 * are separated by " | ", headings ({@code h1}-{@code h6}, produced for Word heading styles and HTML) become
 * sections, and Tika's {@code <div class="page">} elements (PDF) become page spans. Head, script and style content
 * is ignored.
 */
final class XhtmlStructureHandler extends DefaultHandler {

    private static final Set<String> PARAGRAPHS = Set.of("p", "div", "section", "article", "header", "footer",
            "blockquote", "pre", "table", "ul", "ol", "dl", "figure", "main", "aside", "nav");
    private static final Set<String> LINES = Set.of("br", "li", "tr", "dt", "dd", "caption");
    private static final Set<String> IGNORED = Set.of("head", "script", "style", "noscript", "template");

    private final StructuredTextBuilder builder;
    private final Deque<Boolean> pageDivs = new ArrayDeque<>();
    private int ignoredDepth;
    private int pageNumber;
    private int pageStart;
    private StringBuilder heading;
    private int headingLevel;
    private int headingStart;
    private boolean firstCellInRow;

    XhtmlStructureHandler(int maxCharacters) {
        this.builder = new StructuredTextBuilder(maxCharacters);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        String name = name(localName, qName);
        if (IGNORED.contains(name)) {
            ignoredDepth++;
            return;
        }
        if (ignoredDepth > 0) {
            return;
        }
        if ("div".equals(name)) {
            boolean page = "page".equals(attributes.getValue("class"));
            pageDivs.push(page);
            if (page) {
                builder.paragraphBreak();
                pageNumber++;
                pageStart = builder.nextOffset();
                return;
            }
        }
        if (isHeading(name)) {
            builder.paragraphBreak();
            heading = new StringBuilder();
            headingLevel = name.charAt(1) - '0';
            headingStart = builder.nextOffset();
        } else if (PARAGRAPHS.contains(name)) {
            builder.paragraphBreak();
        } else if (LINES.contains(name)) {
            builder.lineBreak();
            firstCellInRow = "tr".equals(name);
        } else if ("td".equals(name) || "th".equals(name)) {
            if (!firstCellInRow) {
                builder.append(" | ");
            }
            firstCellInRow = false;
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        String name = name(localName, qName);
        if (IGNORED.contains(name)) {
            ignoredDepth = Math.max(0, ignoredDepth - 1);
            return;
        }
        if (ignoredDepth > 0) {
            return;
        }
        if ("div".equals(name)) {
            boolean page = !pageDivs.isEmpty() && pageDivs.pop();
            if (page) {
                builder.addPage(pageNumber, pageStart);
            }
            builder.paragraphBreak();
            return;
        }
        if (isHeading(name) && heading != null) {
            builder.addSection(heading.toString(), headingLevel, headingStart);
            heading = null;
            builder.paragraphBreak();
        } else if (PARAGRAPHS.contains(name)) {
            builder.paragraphBreak();
        } else if (LINES.contains(name)) {
            builder.lineBreak();
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (ignoredDepth > 0 || length == 0) {
            return;
        }
        String chunk = new String(ch, start, length);
        if (heading != null) {
            heading.append(chunk);
        }
        builder.append(chunk);
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) {
        if (ignoredDepth == 0) {
            builder.space();
        }
    }

    StructuredTextBuilder builder() {
        return builder;
    }

    private static boolean isHeading(String name) {
        return name.length() == 2 && name.charAt(0) == 'h' && name.charAt(1) >= '1' && name.charAt(1) <= '6';
    }

    private static String name(String localName, String qName) {
        String name = localName == null || localName.isEmpty() ? qName : localName;
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}
