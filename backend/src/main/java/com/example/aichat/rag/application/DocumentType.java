package com.example.aichat.rag.application;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Accepted upload formats. The extension selects the format; the detected content must be compatible with it,
 * so a renamed executable or archive is rejected. Further formats need an extractor plus an entry here.
 */
public enum DocumentType {

    PDF("PDF", "application/pdf", Set.of("pdf"), false, Set.of("application/pdf")),
    DOCX("DOCX", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", Set.of("docx"), false,
            Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/x-tika-ooxml", "application/zip")),
    HTML("HTML", "text/html", Set.of("html", "htm", "xhtml"), true, Set.of("application/xhtml+xml")),
    MARKDOWN("Markdown", "text/markdown", Set.of("md", "markdown", "mdown"), true, Set.of()),
    TEXT("Text", "text/plain", Set.of("txt", "text", "log"), true, Set.of()),
    CSV("CSV", "text/csv", Set.of("csv"), true, Set.of()),
    JSON("JSON", "application/json", Set.of("json"), true, Set.of("application/json"));

    private final String label;
    private final String mimeType;
    private final Set<String> extensions;
    private final boolean textual;
    private final Set<String> acceptedDetections;

    DocumentType(String label, String mimeType, Set<String> extensions, boolean textual,
                 Set<String> acceptedDetections) {
        this.label = label;
        this.mimeType = mimeType;
        this.extensions = extensions;
        this.textual = textual;
        this.acceptedDetections = acceptedDetections;
    }

    public String label() {
        return label;
    }

    public String mimeType() {
        return mimeType;
    }

    public boolean isTextual() {
        return textual;
    }

    public Set<String> extensions() {
        return extensions;
    }

    /** Whether content detected as {@code detectedType} may carry this format's extension. */
    public boolean accepts(String detectedType) {
        String type = detectedType == null ? "" : detectedType.toLowerCase(Locale.ROOT);
        return acceptedDetections.contains(type) || (textual && type.startsWith("text/"));
    }

    public static Optional<DocumentType> fromFilename(String filename) {
        if (filename == null) {
            return Optional.empty();
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return Optional.empty();
        }
        String extension = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(type -> type.extensions.contains(extension)).findFirst();
    }

    /** Human-readable list such as "PDF, DOCX, HTML, ...". */
    public static String supportedList() {
        return String.join(", ", Arrays.stream(values()).map(DocumentType::label).toList());
    }

    /** Short label for a stored MIME type (for the admin list). */
    public static String labelFor(String mimeType) {
        return Arrays.stream(values()).filter(type -> type.mimeType.equalsIgnoreCase(mimeType == null ? "" : mimeType))
                .map(DocumentType::label).findFirst().orElse("File");
    }
}
