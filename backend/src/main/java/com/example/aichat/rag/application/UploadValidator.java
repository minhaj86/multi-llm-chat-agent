package com.example.aichat.rag.application;

import com.example.aichat.application.common.ValidationException;
import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Validates uploads before anything is stored: safe file name (no paths, control or reserved characters), size,
 * supported extension, and content that actually matches the extension (magic-number detection). The original
 * file name is only ever used as metadata, never as a file-system path.
 */
@Component
public class UploadValidator {

    private static final int MAX_FILENAME_LENGTH = 255;
    private static final int BINARY_PROBE_BYTES = 8192;

    private final ContentTypeDetector detector;
    private final RagProperties properties;

    public UploadValidator(ContentTypeDetector detector, RagProperties properties) {
        this.detector = detector;
        this.properties = properties;
    }

    public UploadedFile validate(String originalFilename, byte[] content) {
        String filename = sanitizeFilename(originalFilename);
        if (content == null || content.length == 0) {
            throw new ValidationException("EMPTY_FILE", "The file is empty");
        }
        long max = properties.ingestion().maxFileSize().toBytes();
        if (content.length > max) {
            throw new ValidationException("FILE_TOO_LARGE", "The file is larger than the limit of "
                    + properties.ingestion().maxFileSize().toMegabytes() + " MB");
        }
        DocumentType type = DocumentType.fromFilename(filename).orElseThrow(() -> new ValidationException(
                "UNSUPPORTED_FILE_TYPE", "Unsupported file type. Supported: " + DocumentType.supportedList()));
        String detected = detector.detect(content, filename);
        if (!type.accepts(detected)) {
            throw new ValidationException("CONTENT_MISMATCH", "The file content (" + detected
                    + ") does not match its extension (" + type.label() + ")");
        }
        if (type.isTextual() && looksBinary(content)) {
            throw new ValidationException("CONTENT_MISMATCH", "The file looks binary, not like " + type.label());
        }
        return new UploadedFile(filename, type, content, Checksums.sha256(content));
    }

    /** Last path segment only, without control/reserved characters, at most 255 characters (extension kept). */
    static String sanitizeFilename(String raw) {
        if (raw == null) {
            throw new ValidationException("INVALID_FILENAME", "The file has no name");
        }
        String name = Normalizer.normalize(raw, Normalizer.Form.NFC);
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}<>:\"|?*]", "").replaceAll("\\s+", " ").strip();
        name = name.replaceAll("^[.\\s]+", "");
        if (name.isEmpty() || name.chars().allMatch(c -> c == '.')) {
            throw new ValidationException("INVALID_FILENAME", "The file name is not valid");
        }
        if (name.length() > MAX_FILENAME_LENGTH) {
            int dot = name.lastIndexOf('.');
            String extension = dot > 0 && name.length() - dot <= 16 ? name.substring(dot) : "";
            name = name.substring(0, MAX_FILENAME_LENGTH - extension.length()) + extension;
        }
        return name;
    }

    private static boolean looksBinary(byte[] content) {
        int limit = Math.min(content.length, BINARY_PROBE_BYTES);
        boolean utf16 = limit >= 2 && ((content[0] & 0xFF) == 0xFE && (content[1] & 0xFF) == 0xFF
                || (content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xFE);
        if (utf16) {
            return false;
        }
        int suspicious = 0;
        for (int i = 0; i < limit; i++) {
            int b = content[i] & 0xFF;
            if (b == 0) {
                return true;
            }
            if (b < 0x09 || (b > 0x0D && b < 0x20 && b != 0x1B)) {
                suspicious++;
            }
        }
        return suspicious > limit / 10;
    }

    public record UploadedFile(String filename, DocumentType type, byte[] content, String checksum) {

        public String extension() {
            int dot = filename.lastIndexOf('.');
            return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        }
    }
}
