package com.example.aichat.rag.api;

import com.example.aichat.api.error.Problems;
import com.example.aichat.rag.application.DuplicateKnowledgeException;
import com.example.aichat.rag.domain.EmbeddingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RAG-specific error responses (RFC 9457), scoped to the knowledge-base controllers; everything else is handled
 * by the global handler.
 */
@RestControllerAdvice(assignableTypes = {RagAdminController.class, RagSearchController.class,
        KnowledgeBaseController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class KnowledgeExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeExceptionHandler.class);

    /** 409 with the existing sources, so the UI can offer "Upload as new version" or "Replace existing". */
    @ExceptionHandler(DuplicateKnowledgeException.class)
    public ProblemDetail duplicate(DuplicateKnowledgeException e) {
        ProblemDetail problem = Problems.of(HttpStatus.CONFLICT, e.code(), e.getMessage());
        problem.setProperty("existing", e.existing());
        return problem;
    }

    @ExceptionHandler(EmbeddingException.class)
    public ProblemDetail embedding(EmbeddingException e) {
        log.warn("Embedding provider error during a knowledge-base request: {}", e.getMessage());
        ProblemDetail problem = Problems.of(HttpStatus.SERVICE_UNAVAILABLE, "EMBEDDING_UNAVAILABLE",
                "The embedding model is not available: " + e.getMessage());
        problem.setProperty("retryable", e.isRetryable());
        return problem;
    }
}
