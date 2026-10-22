# Architecture decision records

Significant architectural decisions are recorded here, using a lightweight
[Nygard-style](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions) format: context, decision,
consequences and alternatives considered.

| ADR | Title | Status |
|-----|-------|--------|
| [0001](0001-hexagonal-architecture.md) | Hexagonal (ports and adapters) backend | Accepted |
| [0002](0002-provider-port-registry-and-factories.md) | `LlmProvider` port with registry and factories | Accepted |
| [0003](0003-openai-compatible-adapter-for-local-models.md) | One OpenAI-compatible adapter for local models | Accepted |
| [0004](0004-server-sent-events-for-streaming.md) | Server-Sent Events over POST for streaming | Accepted |
| [0005](0005-servlet-stack-with-virtual-threads.md) | Servlet stack with virtual threads, WebClient for outbound calls | Accepted |
| [0006](0006-summary-plus-recent-context-strategy.md) | `SUMMARY_PLUS_RECENT` as the default context strategy | Accepted |
| [0007](0007-retry-only-when-safe.md) | Retry LLM calls only when safe | Accepted |
| [0008](0008-stateless-jwt-with-live-account-checks.md) | Stateless JWT with live account checks | Accepted |
| [0009](0009-rag-module-with-pgvector.md) | Knowledge base as a RAG module behind ports, with PostgreSQL + pgvector | Accepted |
| [0010](0010-asynchronous-ingestion-and-index-consistency.md) | Asynchronous ingestion with row-locked, transactional index updates | Accepted |
| [0011](0011-browser-audio-and-independent-transcription.md) | Browser audio capture with independent transcription | Proposed |

## Writing a new ADR

1. Copy the structure of an existing record into `NNNN-short-title.md`, using the next number.
2. Status starts as `Proposed`. Change it to `Accepted` when merged. Never rewrite an accepted ADR: supersede it with
   a new one and set the old status to `Superseded by NNNN`.
3. Add the record to the table above and, if it changes the structure, update [architecture.md](../architecture.md).

Write an ADR when a change affects the architecture invariants in [AGENTS.md](../../AGENTS.md): layering, provider
neutrality, the streaming protocol, retry semantics, security model, persistence technology, or a new kind of
external dependency.
