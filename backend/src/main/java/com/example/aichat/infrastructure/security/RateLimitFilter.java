package com.example.aichat.infrastructure.security;

import com.example.aichat.application.auth.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies the configured rate-limit rules. Registered inside the security filter chain after authentication so
 * that limits can be keyed by user. Every matching rule must allow the request.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitProperties properties;
    private final TokenBucketRateLimiter limiter;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public RateLimitFilter(RateLimitProperties properties, TokenBucketRateLimiter limiter, ObjectMapper objectMapper) {
        this.properties = properties;
        this.limiter = limiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled() || properties.rules().isEmpty() || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        for (RateLimitProperties.Rule rule : properties.rules()) {
            if (!applies(rule, request.getMethod(), path)) {
                continue;
            }
            String key = rule.name() + ":" + clientKey(rule, request);
            TokenBucketRateLimiter.Decision decision = limiter.tryConsume(key, rule.capacity(), rule.refillPeriod());
            if (!decision.allowed()) {
                log.info("Rate limit '{}' exceeded for {}", rule.name(), rule.key());
                reject(request, response, decision);
                return;
            }
            response.setHeader("X-RateLimit-Remaining", Long.toString(decision.remaining()));
        }
        chain.doFilter(request, response);
    }

    private boolean applies(RateLimitProperties.Rule rule, String method, String path) {
        boolean methodMatches = rule.methods().isEmpty()
                || rule.methods().stream().anyMatch(m -> m.toUpperCase(Locale.ROOT).equals(method));
        return methodMatches && rule.paths().stream().anyMatch(pattern -> matcher.match(pattern, path));
    }

    private static String clientKey(RateLimitProperties.Rule rule, HttpServletRequest request) {
        if (rule.key() == RateLimitProperties.KeyType.USER) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user) {
                return "user:" + user.id();
            }
        }
        return "ip:" + request.getRemoteAddr();
    }

    private void reject(HttpServletRequest request, HttpServletResponse response,
                        TokenBucketRateLimiter.Decision decision) throws IOException {
        long retryAfterSeconds = Math.max(1, (long) Math.ceil(decision.retryAfter().toMillis() / 1000.0));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests. Please retry in " + retryAfterSeconds + " seconds.");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "RATE_LIMITED");
        problem.setProperty("retryAfterSeconds", retryAfterSeconds);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        response.setHeader("X-RateLimit-Remaining", "0");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
