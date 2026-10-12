package com.example.aichat.rag.infrastructure.extractor;

import com.example.aichat.rag.application.ContentTypeDetector;
import java.io.IOException;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.springframework.stereotype.Component;

/**
 * Content sniffing with Apache Tika's default detector (magic bytes plus OOXML container inspection).
 */
@Component
public class TikaContentTypeDetector implements ContentTypeDetector {

    private final Detector detector = TikaDocumentParser.PARSER.getDetector();

    @Override
    public String detect(byte[] content, String filename) {
        Metadata metadata = new Metadata();
        if (filename != null) {
            metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
        }
        try (TikaInputStream stream = TikaInputStream.get(content)) {
            MediaType type = detector.detect(stream, metadata, new ParseContext());
            return type == null ? MediaType.OCTET_STREAM.toString() : type.getBaseType().toString();
        } catch (IOException e) {
            return MediaType.OCTET_STREAM.toString();
        }
    }
}
