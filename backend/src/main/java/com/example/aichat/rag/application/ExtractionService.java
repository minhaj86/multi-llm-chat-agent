package com.example.aichat.rag.application;

import com.example.aichat.rag.domain.DocumentExtractionException;
import com.example.aichat.rag.domain.DocumentExtractor;
import com.example.aichat.rag.domain.DocumentMetadata;
import com.example.aichat.rag.domain.ExtractedDocument;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

/**
 * Chooses the extractor for a document and runs it with a time limit (protects the ingestion workers against
 * pathological files). Extractors are ordered beans, so more specific ones win.
 */
@Service
public class ExtractionService implements DisposableBean {

    private final List<DocumentExtractor> extractors;
    private final RagProperties properties;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ExtractionService(List<DocumentExtractor> extractors, RagProperties properties) {
        this.extractors = List.copyOf(extractors);
        this.properties = properties;
    }

    public Optional<DocumentExtractor> extractorFor(String contentType, String filename) {
        return extractors.stream().filter(e -> e.supports(contentType, filename)).findFirst();
    }

    public Extraction extract(byte[] content, DocumentMetadata metadata) {
        DocumentExtractor extractor = extractorFor(metadata.contentType(), metadata.filename())
                .orElseThrow(() -> new DocumentExtractionException("No text extractor for "
                        + (metadata.contentType() == null ? "this file type" : metadata.contentType())));
        Future<ExtractedDocument> future = executor.submit(() ->
                extractor.extract(new ByteArrayInputStream(content), metadata));
        ExtractedDocument document;
        try {
            document = future.get(properties.ingestion().extractionTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new DocumentExtractionException("Text extraction timed out after "
                    + properties.ingestion().extractionTimeout().toSeconds() + " s", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new DocumentExtractionException("Text extraction was interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof DocumentExtractionException extraction) {
                throw extraction;
            }
            if (cause instanceof IOException io) {
                throw new DocumentExtractionException("The document could not be read: " + io.getMessage(), io);
            }
            throw new DocumentExtractionException("Text extraction failed: " + (cause == null ? e.getMessage()
                    : cause.getClass().getSimpleName() + (cause.getMessage() == null ? "" : ": "
                    + cause.getMessage())), cause);
        }
        if (document.text().isBlank()) {
            throw new DocumentExtractionException("No text could be extracted. Scanned documents need OCR, which "
                    + "is not supported yet.");
        }
        return new Extraction(document, extractor.name());
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }

    public record Extraction(ExtractedDocument document, String extractor) {
    }
}
