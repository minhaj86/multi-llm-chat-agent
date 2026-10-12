# 0007. Retry LLM calls only when safe

- Status: Accepted
- Date: 2026-10-12

## Context

LLM requests are non-idempotent POSTs. A request that failed from the client's point of view may still have been
processed and billed by the provider. Retrying a stream after tokens were shown would duplicate output. Local model
servers, on the other hand, are often briefly unreachable (restarting, loading a model): retrying a refused connection
is harmless, because nothing was processed.

## Decision

- Retry decisions live in the neutral exception: `LlmException.isRetryable()`, set by the adapter's error translator.
  Retryable failures are:
  - connection refused or unknown host (the request never reached the server);
  - HTTP 502 or 503 (the server did not process the request);
  - HTTP 429 without a quota error, when `Retry-After` is absent or at most `retry.max-rate-limit-wait` (default
    5 s); the wait honours `Retry-After`.
- Never retried:
  - timeouts (the server may still be generating, and billing);
  - authentication errors, invalid requests, unknown models, content filtering;
  - any failure after a stream has emitted its first chunk. `ResilientLlmProvider` marks those errors with
    `nonRetryable()`.
- Retry policy per provider: `retry.max-attempts` (default 2, which means at most one retry), exponential backoff
  from `retry.initial-backoff`.
- The circuit breaker (per provider) records only failures that indicate an unhealthy provider: unavailable,
  timeout, upstream 5xx and interrupted streams. Rate limits and bad requests do not count. When open, calls fail
  fast with `PROVIDER_UNAVAILABLE`, and the UI offers alternative providers.

## Consequences

- No duplicated answers and no double billing caused by automatic retries.
- Transient local restarts are smoothed over without user action.
- Some transient cloud failures (for example a timeout) reach the user, who can regenerate or switch provider with
  one click. That decision stays with the user.

## Alternatives considered

- **Retry every failure (or every 5xx)**: risks duplicated output and charges, and amplifies load on a struggling
  provider.
- **No retries at all**: avoidable errors while local servers restart.
- **Idempotency keys**: not supported by most providers and inference engines.
