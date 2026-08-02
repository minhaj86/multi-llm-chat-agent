package com.example.aichat.api.error;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Factory for RFC 9457 problem details carrying a stable machine-readable {@code code}.
 */
public final class Problems {

    private Problems() {
    }

    public static ProblemDetail of(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("about:blank"));
        problem.setProperty("code", code);
        return problem;
    }

    public static ProblemDetail of(HttpStatus status, String code, String detail) {
        return of((HttpStatusCode) status, code, detail);
    }
}
