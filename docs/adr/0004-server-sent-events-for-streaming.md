# 0004. Server-Sent Events over POST for streaming

- Status: Accepted
- Date: 2026-10-12

## Context

Answers must render token by token, and the user must be able to stop a generation. A chat request carries a body
(message, conversation, provider, model, system prompt) and must be authenticated with a bearer token. The stream
crosses nginx in Docker and the Vite proxy in development. Compare mode needs several concurrent streams in one
response.

## Decision

- `POST /api/chat/stream`, `POST /api/chat/regenerate` and `POST /api/compare/stream` respond with
  `text/event-stream`, using named events:
  - chat: `start`, `delta`, `reasoning`, `done`, `error`, `title`
  - compare: the same names, with a `targetId` in each payload
- A comment heartbeat (`:keep-alive`) every 15 s keeps idle connections open while a local model loads.
- The browser reads the stream with `fetch` and `ReadableStream` and a small incremental SSE parser
  (`frontend/src/api/sse.ts`), because `EventSource` supports only GET without custom headers.
- Stop: the client aborts the request (`AbortController`) and calls
  `POST /api/chat/generations/{assistantMessageId}/cancel`. Either one cancels the upstream LLM call. The partial
  answer is kept with status `CANCELLED`.
- Errors before the stream starts (validation, unknown provider, rate limit) are RFC 9457 problem details, even when
  the client accepts only `text/event-stream`. Errors during the stream are `error` events, including suggested
  alternative providers.
- Proxies must not buffer: nginx uses `proxy_buffering off`, and the backend sends `X-Accel-Buffering: no`.

## Consequences

- Plain HTTP: works through standard proxies, is easy to debug with `curl -N`, and is easy to simulate with WireMock
  in tests.
- One protocol serves chat and compare.
- No automatic resume: a dropped connection ends the generation. The stored message shows the partial or failed
  state, and the user can regenerate.
- One long-lived HTTP request per generation (see [0005](0005-servlet-stack-with-virtual-threads.md)).

## Alternatives considered

- **WebSockets**: bidirectional messaging is not needed. They add connection management, authentication and proxy
  complexity, and are harder to test.
- **`EventSource` with GET and the token in the query string**: leaks tokens into logs and history, and cannot send
  a body.
- **Long polling**: higher latency and more requests.
