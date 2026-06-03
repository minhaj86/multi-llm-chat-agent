package com.example.aichat.infrastructure.llm;

import com.example.aichat.common.Texts;
import com.example.aichat.domain.llm.exception.LlmAuthenticationException;
import com.example.aichat.domain.llm.exception.LlmErrorCode;
import com.example.aichat.domain.llm.exception.LlmException;
import com.example.aichat.domain.llm.exception.LlmProviderUnavailableException;
import com.example.aichat.domain.llm.exception.LlmRateLimitException;
import com.example.aichat.domain.llm.exception.LlmResponseException;
import com.example.aichat.domain.llm.exception.LlmTimeoutException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.timeout.ReadTimeoutException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.codec.CodecException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

/**
 * Maps vendor/transport failures of OpenAI style APIs ({@code {"error": {"message", "type", "code"}}}) into the
 * provider-neutral exception hierarchy. Shared by the OpenAI and the OpenAI-compatible adapters.
 *
 * <p>Retry safety: only failures where the provider certainly did not process the request (connection could not be
 * established, 502/503, short rate-limit waits) are marked retryable. Timeouts and 500s are not retried because the
 * provider may already have generated (and billed) a response.
 */
public class LlmErrorTranslator {

    private static final Logger log = LoggerFactory.getLogger(LlmErrorTranslator.class);
    private static final int MAX_DETAIL_LENGTH = 400;

    private final String providerId;
    private final String providerName;
    private final ObjectMapper objectMapper;
    private final Duration maxRateLimitWait;

    public LlmErrorTranslator(String providerId, String providerName, ObjectMapper objectMapper,
                              Duration maxRateLimitWait) {
        this.providerId = providerId;
        this.providerName = providerName;
        this.objectMapper = objectMapper;
        this.maxRateLimitWait = maxRateLimitWait;
    }

    /** For {@code WebClient.retrieve().onStatus(...)}: reads the error body and translates it. */
    public Mono<? extends Throwable> fromResponse(ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .onErrorReturn("")
                .map(body -> fromStatus(response.statusCode().value(), response.headers().asHttpHeaders(), body));
    }

    public LlmException fromStatus(int status, HttpHeaders headers, String body) {
        ErrorInfo info = parse(body);
        String detail = Texts.hasText(info.message()) ? Texts.truncate(info.message().strip(), MAX_DETAIL_LENGTH)
                : "HTTP " + status;
        log.warn("Provider {} responded with HTTP {} (type={}, code={})", providerId, status, info.type(), info.code());
        return switch (status) {
            case 400, 422 -> isModelNotFound(info)
                    ? new LlmResponseException(providerId, LlmErrorCode.MODEL_NOT_FOUND, detail, status, null)
                    : new LlmResponseException(providerId, contentFilter(info) ? LlmErrorCode.CONTENT_FILTERED
                    : LlmErrorCode.INVALID_REQUEST, detail, status, null);
            case 401 -> new LlmAuthenticationException(providerId, providerName + " rejected the API key");
            case 403 -> new LlmAuthenticationException(providerId, providerName + " denied access to this resource");
            case 404 -> isModelNotFound(info) || mentionsModel(info)
                    ? new LlmResponseException(providerId, LlmErrorCode.MODEL_NOT_FOUND, detail, status, null)
                    : new LlmResponseException(providerId, LlmErrorCode.UPSTREAM_ERROR,
                    "Endpoint not found - check the base URL of " + providerName, status, null);
            case 408 -> new LlmTimeoutException(providerId, providerName + " timed out (HTTP 408)", null);
            case 413 -> new LlmResponseException(providerId, LlmErrorCode.INVALID_REQUEST,
                    "The request is too large for " + providerName, status, null);
            case 429 -> rateLimit(headers, info, detail);
            case 502, 503 -> new LlmProviderUnavailableException(providerId,
                    providerName + " is unavailable (HTTP " + status + ")", true, null);
            case 504 -> new LlmTimeoutException(providerId, providerName + " gateway timeout (HTTP 504)", null);
            default -> status >= 500
                    ? new LlmResponseException(providerId, LlmErrorCode.UPSTREAM_ERROR, detail, status, null)
                    : new LlmResponseException(providerId, LlmErrorCode.INVALID_REQUEST, detail, status, null);
        };
    }

    /** Translates an error object that arrived inside an SSE stream or a failed response. */
    public LlmException fromErrorObject(JsonNode error) {
        String code = text(error, "code");
        String type = text(error, "type");
        String message = Texts.firstNonBlank(text(error, "message"), "Stream error");
        ErrorInfo info = new ErrorInfo(message, type, code);
        if ("rate_limit_exceeded".equals(code) || "insufficient_quota".equals(code)
                || "rate_limit_error".equals(type)) {
            return rateLimit(HttpHeaders.EMPTY, info, message);
        }
        if (isModelNotFound(info)) {
            return new LlmResponseException(providerId, LlmErrorCode.MODEL_NOT_FOUND, message, null, null);
        }
        if (contentFilter(info)) {
            return new LlmResponseException(providerId, LlmErrorCode.CONTENT_FILTERED, message, null, null);
        }
        if ("server_error".equals(type) || "server_error".equals(code)) {
            return new LlmResponseException(providerId, LlmErrorCode.UPSTREAM_ERROR,
                    Texts.truncate(message, MAX_DETAIL_LENGTH), null, null);
        }
        return new LlmResponseException(providerId, LlmErrorCode.UPSTREAM_ERROR,
                Texts.truncate(message, MAX_DETAIL_LENGTH), null, null);
    }

    /** For {@code onErrorMap}: translates transport level failures; already translated failures pass through. */
    public Throwable translate(Throwable error) {
        if (error instanceof LlmException) {
            return error;
        }
        if (error instanceof WebClientResponseException response) {
            return fromStatus(response.getStatusCode().value(), response.getHeaders(),
                    response.getResponseBodyAsString());
        }
        if (error instanceof WebClientRequestException request) {
            Throwable cause = request.getMostSpecificCause();
            if (isConnectFailure(cause)) {
                return new LlmProviderUnavailableException(providerId,
                        providerName + " is not reachable (" + describe(cause) + ")", true, error);
            }
            if (cause instanceof ReadTimeoutException || cause instanceof TimeoutException) {
                return new LlmTimeoutException(providerId, providerName + " did not respond in time", error);
            }
            return new LlmProviderUnavailableException(providerId,
                    providerName + " request failed (" + describe(cause) + ")", false, error);
        }
        if (error instanceof TimeoutException || error instanceof ReadTimeoutException) {
            return new LlmTimeoutException(providerId, providerName + " did not respond in time", error);
        }
        if (error instanceof PrematureCloseException) {
            return new LlmResponseException(providerId, LlmErrorCode.STREAM_INTERRUPTED,
                    providerName + " closed the connection unexpectedly", null, error);
        }
        if (error instanceof CodecException || error instanceof JsonProcessingException) {
            return new LlmResponseException(providerId, LlmErrorCode.UPSTREAM_ERROR,
                    providerName + " returned an invalid response", null, error);
        }
        if (isConnectFailure(error)) {
            return new LlmProviderUnavailableException(providerId,
                    providerName + " is not reachable (" + describe(error) + ")", true, error);
        }
        return new LlmResponseException(providerId, LlmErrorCode.UPSTREAM_ERROR,
                providerName + " request failed: " + describe(error), null, error);
    }

    private LlmRateLimitException rateLimit(HttpHeaders headers, ErrorInfo info, String detail) {
        boolean quota = "insufficient_quota".equals(info.code()) || "insufficient_quota".equals(info.type());
        Duration retryAfter = retryAfter(headers);
        boolean retryable = !quota && (retryAfter == null || retryAfter.compareTo(maxRateLimitWait) <= 0);
        return new LlmRateLimitException(providerId, quota ? providerName + " quota exceeded" : detail, retryAfter,
                quota, retryable, null);
    }

    static Duration retryAfter(HttpHeaders headers) {
        String millis = headers.getFirst("retry-after-ms");
        if (millis != null) {
            try {
                return Duration.ofMillis((long) Double.parseDouble(millis.strip()));
            } catch (NumberFormatException ignored) {
                // fall through to Retry-After
            }
        }
        String seconds = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (seconds != null) {
            try {
                return Duration.ofMillis((long) (Double.parseDouble(seconds.strip()) * 1000));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private ErrorInfo parse(String body) {
        if (Texts.isBlank(body)) {
            return new ErrorInfo(null, null, null);
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode error = root.path("error");
            if (error.isObject()) {
                return new ErrorInfo(text(error, "message"), text(error, "type"), text(error, "code"));
            }
            if (error.isTextual()) {
                return new ErrorInfo(error.asText(), text(root, "type"), text(root, "code"));
            }
            return new ErrorInfo(Texts.firstNonBlank(text(root, "message"), text(root, "detail")),
                    text(root, "type"), text(root, "code"));
        } catch (JsonProcessingException e) {
            return new ErrorInfo(Texts.truncate(body.strip(), MAX_DETAIL_LENGTH), null, null);
        }
    }

    private static boolean isModelNotFound(ErrorInfo info) {
        if ("model_not_found".equals(info.code())) {
            return true;
        }
        String message = info.message() == null ? "" : info.message().toLowerCase(Locale.ROOT);
        return message.contains("model") && (message.contains("not found") || message.contains("does not exist")
                || message.contains("not exist") || message.contains("no such model") || message.contains("unknown model"));
    }

    private static boolean mentionsModel(ErrorInfo info) {
        return info.message() != null && info.message().toLowerCase(Locale.ROOT).contains("model");
    }

    private static boolean contentFilter(ErrorInfo info) {
        return "content_filter".equals(info.code()) || "content_policy_violation".equals(info.code());
    }

    private static boolean isConnectFailure(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof ConnectException || current instanceof UnknownHostException
                    || current instanceof NoRouteToHostException || current instanceof UnresolvedAddressException) {
                return true;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }

    private static String describe(Throwable error) {
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (Texts.hasText(message) ? ": " + Texts.truncate(message, 200) : "");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private record ErrorInfo(String message, String type, String code) {
    }
}
