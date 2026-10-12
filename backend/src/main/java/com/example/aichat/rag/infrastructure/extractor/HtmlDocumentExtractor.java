package com.example.aichat.rag.infrastructure.extractor;

import com.example.aichat.rag.application.RagProperties;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * HTML pages: visible text only. Scripts, styles and markup are dropped by Tika, and the extracted text is never
 * rendered as HTML anywhere, so malicious markup cannot reach a browser.
 */
@Component
@Order(30)
public class HtmlDocumentExtractor extends TikaBackedExtractor {

    public HtmlDocumentExtractor(RagProperties properties) {
        super(Set.of("text/html", "application/xhtml+xml"), Set.of("html", "htm", "xhtml"), properties);
    }

    @Override
    public String name() {
        return "html";
    }
}
