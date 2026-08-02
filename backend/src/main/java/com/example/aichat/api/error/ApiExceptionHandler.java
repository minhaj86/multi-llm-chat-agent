package com.example.aichat.api.error;

import com.example.aichat.application.auth.AccountUnavailableException;
import com.example.aichat.application.auth.InvalidCredentialsException;
import com.example.aichat.application.common.ConflictException;
import com.example.aichat.application.common.ResourceNotFoundException;
import com.example.aichat.application.common.TooManyRequestsException;
import com.example.aichat.application.common.ValidationException;
import com.example.aichat.application.provider.LlmErrorMessages;
import com.example.aichat.application.provider.ProviderCatalogService;
import com.example.aichat.application.provider.ProviderDisabledException;
import com.example.aichat.application.provider.ProviderStatus;
import com.example.aichat.application.provider.UnknownProviderException;
import com.example.aichat.domain.llm.exception.LlmException;
import com.example.aichat.domain.llm.exception.LlmRateLimitException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps exceptions to RFC 9457 problem details with a stable {@code code}. Internal details are never leaked:
 * unexpected errors return a generic message and are logged with the correlation id.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final ProviderCatalogService catalog;

    public ApiExceptionHandler(ProviderCatalogService catalog) {
        this.catalog = catalog;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail notFound(ResourceNotFoundException e) {
        return Problems.of(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    ProblemDetail validation(ValidationException e) {
        return Problems.of(HttpStatus.BAD_REQUEST, e.code(), e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException e) {
        return Problems.of(HttpStatus.CONFLICT, e.code(), e.getMessage());
    }

    @ExceptionHandler(TooManyRequestsException.class)
    ResponseEntity<ProblemDetail> tooManyRequests(TooManyRequestsException e) {
        long seconds = Math.max(1, e.retryAfter() == null ? 1 : e.retryAfter().toSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
                .body(Problems.of(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", e.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(InvalidCredentialsException e) {
        return Problems.of(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", e.getMessage());
    }

    @ExceptionHandler(AccountUnavailableException.class)
    ProblemDetail accountUnavailable(AccountUnavailableException e) {
        return Problems.of(HttpStatus.FORBIDDEN, e.code(), e.getMessage());
    }

    @ExceptionHandler(UnknownProviderException.class)
    ProblemDetail unknownProvider(UnknownProviderException e) {
        ProblemDetail problem = Problems.of(HttpStatus.NOT_FOUND, "UNKNOWN_PROVIDER", e.getMessage());
        problem.setProperty("provider", e.providerId());
        return problem;
    }

    @ExceptionHandler(ProviderDisabledException.class)
    ProblemDetail providerDisabled(ProviderDisabledException e) {
        ProblemDetail problem = Problems.of(HttpStatus.CONFLICT, "PROVIDER_DISABLED", e.getMessage());
        problem.setProperty("provider", e.providerId());
        problem.setProperty("alternatives", catalog.alternativesTo(e.providerId()));
        return problem;
    }

    @ExceptionHandler(LlmException.class)
    ResponseEntity<ProblemDetail> llmFailure(LlmException e) {
        HttpStatus status = LlmHttpStatus.forCode(e.code().name());
        String providerName = catalog.providers().stream()
                .filter(p -> p.id().equals(e.providerId()))
                .map(ProviderStatus::name)
                .findFirst()
                .orElse(e.providerId());
        ProblemDetail problem = Problems.of(status, e.code().name(), LlmErrorMessages.describe(e, providerName));
        problem.setProperty("provider", e.providerId());
        problem.setProperty("retryable", LlmErrorMessages.retryable(e));
        problem.setProperty("alternatives", catalog.alternativesTo(e.providerId()));
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status);
        if (e instanceof LlmRateLimitException rateLimit && rateLimit.retryAfter().isPresent()) {
            response.header(HttpHeaders.RETRY_AFTER,
                    Long.toString(Math.max(1, rateLimit.retryAfter().get().toSeconds())));
        }
        return response.body(problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail dataIntegrity(DataIntegrityViolationException e) {
        log.info("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return Problems.of(HttpStatus.CONFLICT, "CONFLICT", "The request conflicts with existing data");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail accessDenied(AccessDeniedException e) {
        return Problems.of(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Unexpected error", e);
        return Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail problem = Problems.of(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, @NonNull HttpHeaders headers,
                                                          @NonNull HttpStatusCode statusCode,
                                                          @NonNull WebRequest request) {
        if (body instanceof ProblemDetail problem && problem.getProperties() == null) {
            problem.setProperty("code", HttpStatus.valueOf(statusCode.value()).name());
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }
}
