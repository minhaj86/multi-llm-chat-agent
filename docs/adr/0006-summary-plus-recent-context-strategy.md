# 0006. `SUMMARY_PLUS_RECENT` as the default context strategy

- Status: Accepted
- Date: 2026-10-12

## Context

Resending the whole conversation on every turn grows cost and latency without bound and eventually exceeds context
windows. Local models often have 8k to 32k token windows. Users switch providers mid-conversation, so whatever is
sent must be provider-neutral text. The system prompt must stay intact and separate from the history.

## Decision

`ConversationContextManager` builds every request from a configurable strategy (`chat.context.strategy`):

| Strategy | Sent history |
|----------|--------------|
| `FULL_CONTEXT` | Every completed message |
| `LAST_N_MESSAGES` | The last N messages (`last-n-messages`, default 20), trimmed to the token budget |
| `TOKEN_WINDOW` | The newest messages that fit `max-context-tokens` (default 8000) |
| `SUMMARY_PLUS_RECENT` | A rolling summary of older messages + the newest messages that fit the budget (**default**) |

- The system prompt and the summary are merged into one leading system message. The history follows, with roles
  normalised to alternate, and failed or in-flight answers excluded.
- `ConversationSummarizer` runs **in the background after an answer completes**. When unsummarised history exceeds
  `summary-trigger-tokens` (6000), all but the last `summary-keep-recent-messages` (6) are folded into the stored
  summary (`conversations.summary`, `summarized_until`). It uses the conversation's current provider, or a dedicated
  `chat.summary.provider`/`model`. At most one summarisation runs per conversation.
- Token counts are estimated by a `TokenEstimator` (character heuristic, about 3.5 characters per token). Actual
  usage is taken from provider responses.

## Consequences

- Bounded request size and cost, and long conversations keep working on small local models.
- Summaries never add latency to an answer, because they run after it.
- Summaries lose detail. Very old specifics may be paraphrased or dropped.
- Summarisation is an extra LLM call, recorded in `llm_executions` with purpose `SUMMARY`. Its quality depends on the
  model used; a dedicated summary provider can be configured.
- Estimated token counts can be off for unusual tokenizers. A model-specific `TokenEstimator` can replace the
  heuristic without changing the context manager.

## Alternatives considered

- **`FULL_CONTEXT` by default**: simplest, but unbounded growth and hard failures on small context windows.
- **Sliding window only**: cheap, but early facts (names, requirements) silently disappear.
- **Retrieval over the history with embeddings**: better recall, but needs a vector store and embedding model. It is
  kept as a future extension (a retrieval port used by the context manager).
