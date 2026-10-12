# 0005. Servlet stack with virtual threads, WebClient for outbound calls

- Status: Accepted
- Date: 2026-10-12

## Context

The backend uses Spring Security, Spring Data JPA (blocking JDBC), Flyway and Bean Validation: all first-class on the
servlet stack. LLM calls, however, are long-lived streams that must be cancellable, with backpressure, and possibly
many in parallel (chat, compare fan-out, background titles and summaries).

## Decision

- Run Spring MVC on Tomcat with virtual threads (`spring.threads.virtual.enabled: true`).
- Controllers return `Flux<ServerSentEvent<...>>` for streaming endpoints. Spring MVC subscribes to the `Flux` and
  writes each event as it arrives; a client disconnect cancels the subscription.
- Make every outbound LLM call with `WebClient` (Reactor Netty): non-blocking I/O, natural streaming
  (`bodyToFlux`), and cancellation that closes the upstream connection. Each provider gets its own connection pool
  and connect timeout (`LlmWebClientFactory`).
- Keep blocking work off the Netty event loop. Messages are persisted on the request's virtual thread before
  streaming starts. Completion and failure are finalized on `Schedulers.boundedElastic()`, and cancellations on a
  background executor (`BackgroundTasks`).

## Consequences

- Familiar imperative code for security, persistence and administration, with reactive streams only where streaming
  needs them.
- Thousands of concurrent SSE connections are affordable: each request runs on a cheap virtual thread, and upstream
  I/O is non-blocking.
- Two programming models coexist. Contributors must never block inside Reactor operators running on Netty threads,
  and must keep repository calls on bounded-elastic or virtual threads.
- Resilience4j's reactive operators apply directly to the provider `Mono`/`Flux`.

## Alternatives considered

- **Full WebFlux with R2DBC**: end-to-end reactive, but it loses JPA and the blocking ecosystem, and makes simple
  CRUD and security code more complex for little gain at this scale.
- **`SseEmitter` with a blocking SDK streaming loop**: simple, but cancellation is cooperative and slow, and a
  platform thread per stream would limit concurrency without virtual threads.
