package com.example.aichat.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns a correlation id to every request (re-using a well-formed incoming {@code X-Correlation-Id} /
 * {@code X-Request-Id}), exposes it in the MDC for structured logging and echoes it in the response.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String ALTERNATIVE_HEADER = "X-Request-Id";
    public static final String MDC_KEY = "correlationId";
    public static final String REQUEST_ATTRIBUTE = CorrelationIdFilter.class.getName() + ".id";

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = resolve(request);
        request.setAttribute(REQUEST_ATTRIBUTE, correlationId);
        response.setHeader(HEADER, correlationId);
        try (MDC.MDCCloseable ignored = MDC.putCloseable(MDC_KEY, correlationId)) {
            chain.doFilter(request, response);
        }
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    private static String resolve(HttpServletRequest request) {
        Object existing = request.getAttribute(REQUEST_ATTRIBUTE);
        if (existing instanceof String id) {
            return id;
        }
        for (String header : new String[] {HEADER, ALTERNATIVE_HEADER}) {
            String candidate = request.getHeader(header);
            if (candidate != null && VALID.matcher(candidate).matches()) {
                return candidate;
            }
        }
        return UUID.randomUUID().toString();
    }

    /** Correlation id of the current request, or a fresh id outside of a request. */
    public static String current(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute(REQUEST_ATTRIBUTE);
        if (value instanceof String id) {
            return id;
        }
        String fromMdc = MDC.get(MDC_KEY);
        return fromMdc != null ? fromMdc : UUID.randomUUID().toString();
    }
}
