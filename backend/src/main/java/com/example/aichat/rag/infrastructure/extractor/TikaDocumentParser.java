package com.example.aichat.rag.infrastructure.extractor;

import com.example.aichat.rag.domain.DocumentExtractionException;
import com.example.aichat.rag.domain.DocumentMetadata;
import com.example.aichat.rag.domain.ExtractedDocument;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.ZeroByteFileException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.SAXException;

/**
 * Shared Apache Tika parsing for the binary formats. Embedded documents (attachments, images) are not parsed
 * because no recursive parser is registered in the {@link ParseContext}; Tika's auto-detect parser additionally
 * guards against decompression bombs.
 */
final class TikaDocumentParser {

    static final AutoDetectParser PARSER = new AutoDetectParser();

    private TikaDocumentParser() {
    }

    static ExtractedDocument parse(InputStream input, DocumentMetadata metadata, String contentType,
                                   int maxCharacters) throws IOException {
        Metadata tikaMetadata = new Metadata();
        if (metadata.filename() != null) {
            tikaMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, metadata.filename());
        }
        if (contentType != null) {
            tikaMetadata.set(TikaCoreProperties.CONTENT_TYPE_HINT, contentType);
        }
        XhtmlStructureHandler handler = new XhtmlStructureHandler(maxCharacters);
        try (TikaInputStream stream = TikaInputStream.get(input)) {
            PARSER.parse(stream, handler, tikaMetadata, new ParseContext());
        } catch (EncryptedDocumentException e) {
            throw new DocumentExtractionException("Unsupported encryption: the document is password protected and "
                    + "cannot be indexed", e);
        } catch (ZeroByteFileException e) {
            throw new DocumentExtractionException("The file is empty", e);
        } catch (TikaException | SAXException | RuntimeException e) {
            DocumentExtractionException limit = findCause(e, DocumentExtractionException.class);
            if (limit != null) {
                throw limit;
            }
            if (findCause(e, EncryptedDocumentException.class) != null) {
                throw new DocumentExtractionException("Unsupported encryption: the document is password protected "
                        + "and cannot be indexed", e);
            }
            throw new DocumentExtractionException("The document could not be parsed (" + describe(e) + ")", e);
        }
        StructuredTextBuilder builder = handler.builder();
        Map<String, String> properties = new LinkedHashMap<>();
        copy(tikaMetadata, "dc:title", properties, "title");
        copy(tikaMetadata, "dc:creator", properties, "author");
        copy(tikaMetadata, "xmpTPg:NPages", properties, "pages");
        copy(tikaMetadata, "dc:language", properties, "language");
        copy(tikaMetadata, "Content-Type", properties, "detectedContentType");
        return new ExtractedDocument(metadata, builder.text(), builder.pages(), builder.sections(), properties);
    }

    private static void copy(Metadata from, String key, Map<String, String> to, String name) {
        String value = from.get(key);
        if (value != null && !value.isBlank()) {
            to.put(name, value.length() > 300 ? value.substring(0, 300) : value.strip());
        }
    }

    private static <T extends Throwable> T findCause(Throwable error, Class<T> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return null;
    }

    private static String describe(Exception e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        return message.length() > 200 ? message.substring(0, 200) : message;
    }
}
