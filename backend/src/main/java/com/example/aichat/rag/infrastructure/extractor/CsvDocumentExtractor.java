package com.example.aichat.rag.infrastructure.extractor;

import com.example.aichat.rag.application.RagProperties;
import com.example.aichat.rag.domain.DocumentExtractionException;
import com.example.aichat.rag.domain.DocumentExtractor;
import com.example.aichat.rag.domain.DocumentMetadata;
import com.example.aichat.rag.domain.ExtractedDocument;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * CSV files: every data row becomes one line of {@code column: value} pairs, which keeps the meaning of each value
 * when rows end up in different chunks. The delimiter (comma, semicolon or tab) is detected from the header line.
 */
@Component
@Order(50)
public class CsvDocumentExtractor implements DocumentExtractor {

    private static final int MAX_COLUMNS = 200;

    private final RagProperties properties;

    public CsvDocumentExtractor(RagProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "csv";
    }

    @Override
    public boolean supports(String contentType, String filename) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).split(";")[0].strip();
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return "text/csv".equals(type) || "application/csv".equals(type) || name.endsWith(".csv");
    }

    @Override
    public ExtractedDocument extract(InputStream input, DocumentMetadata metadata) throws IOException {
        String raw = TextDecoding.decode(input.readAllBytes());
        char delimiter = detectDelimiter(raw);
        List<List<String>> rows = parse(raw, delimiter);
        if (rows.isEmpty()) {
            return new ExtractedDocument(metadata, "", List.of(), List.of(), Map.of("rows", "0"));
        }
        List<String> header = rows.getFirst();
        StringBuilder text = new StringBuilder();
        int max = properties.ingestion().maxExtractedCharacters();
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            StringBuilder line = new StringBuilder();
            for (int c = 0; c < Math.min(row.size(), MAX_COLUMNS); c++) {
                String value = row.get(c).strip();
                if (value.isEmpty()) {
                    continue;
                }
                String column = c < header.size() && !header.get(c).isBlank() ? header.get(c).strip()
                        : "column " + (c + 1);
                if (!line.isEmpty()) {
                    line.append(" | ");
                }
                line.append(column).append(": ").append(value.replaceAll("\\s+", " "));
            }
            if (!line.isEmpty()) {
                text.append(line).append('\n');
            }
            if (text.length() > max) {
                throw new DocumentExtractionException("The document text exceeds the limit of " + max
                        + " characters (rag.ingestion.max-extracted-characters)");
            }
        }
        if (rows.size() == 1) {
            text.append(String.join(" | ", header));
        }
        return new ExtractedDocument(metadata, text.toString().strip(), List.of(), List.of(),
                Map.of("rows", Integer.toString(rows.size() - 1), "columns", Integer.toString(header.size())));
    }

    static char detectDelimiter(String text) {
        int end = text.indexOf('\n');
        String header = end < 0 ? text : text.substring(0, end);
        long commas = header.chars().filter(c -> c == ',').count();
        long semicolons = header.chars().filter(c -> c == ';').count();
        long tabs = header.chars().filter(c -> c == '\t').count();
        if (tabs > commas && tabs > semicolons) {
            return '\t';
        }
        return semicolons > commas ? ';' : ',';
    }

    /** RFC 4180 parsing: quoted fields may contain delimiters, quotes ({@code ""}) and line breaks. */
    static List<List<String>> parse(String text, char delimiter) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"' && field.isEmpty()) {
                quoted = true;
            } else if (c == delimiter) {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n') {
                row.add(field.toString());
                field.setLength(0);
                if (!(row.size() == 1 && row.getFirst().isBlank())) {
                    rows.add(row);
                }
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (!field.isEmpty() || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }
}
