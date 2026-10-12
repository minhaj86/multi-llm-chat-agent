package com.example.aichat.rag.infrastructure.extractor;

import com.example.aichat.rag.application.RagProperties;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * PDF text with page numbers (via Apache Tika / PDFBox). Scanned PDFs without a text layer yield no text: OCR is
 * a possible future extension and deliberately not part of this extractor.
 */
@Component
@Order(10)
public class PdfDocumentExtractor extends TikaBackedExtractor {

    public PdfDocumentExtractor(RagProperties properties) {
        super(Set.of("application/pdf"), Set.of("pdf"), properties);
    }

    @Override
    public String name() {
        return "pdf";
    }
}
