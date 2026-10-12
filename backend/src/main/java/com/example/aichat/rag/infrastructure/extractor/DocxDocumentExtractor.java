package com.example.aichat.rag.infrastructure.extractor;

import com.example.aichat.rag.application.RagProperties;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Word documents (DOCX) via Apache Tika / POI; Word heading styles become sections.
 */
@Component
@Order(20)
public class DocxDocumentExtractor extends TikaBackedExtractor {

    public DocxDocumentExtractor(RagProperties properties) {
        super(Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"), Set.of("docx"),
                properties);
    }

    @Override
    public String name() {
        return "docx";
    }
}
