# 0010. Asynchronous ingestion with row-locked, transactional index updates

- Status: Accepted
- Date: 2026-10-12

## Context

Extracting, chunking and embedding a large document can take from seconds to minutes (remote embedding APIs, local
models on a CPU). Upload requests must not block for that long. At the same time the index must stay consistent while
administrators edit metadata, disable, reindex, replace or delete sources, possibly while indexing is still running:
no duplicate vectors, no orphaned vectors, no lost edits, and no outdated content in answers.

## Decision

- Uploads and text entries are validated, stored (source row and original content) and answered with
  `202 Accepted` and status `UPLOADED`. Indexing is submitted to `IngestionQueue` after the transaction commits.
- `IngestionQueue` is a bounded in-process executor (`RAG_INGESTION_CONCURRENCY`). It de-duplicates per source and
  runs a source again if a new request arrives while it is running.
- `IngestionPipeline` moves the source through `EXTRACTING`, `EXTRACTED`, `CHUNKING`, `EMBEDDING` and `INDEXED`
  (or `FAILED` with a readable reason and statistics). Extraction and embedding happen outside database transactions.
- The final write runs in one transaction under a row lock on the source (`SELECT … FOR UPDATE`): it stops if the
  source is `DELETING`, re-reads collection, enabled flag and tags, deletes the previous chunks and vectors and inserts
  the new ones. Metadata changes, enable/disable and content replacement take the same lock.
- Old vectors stay searchable during a reindex and are swapped atomically. When the content itself changes (text
  edit, replaced file), the old chunks and vectors are deleted immediately.
- Deletion is two-phase: mark `DELETING` (running pipelines discard their results), then remove the source, its file,
  extracted text, chunks and vectors in one transaction.
- On startup, `KnowledgeMaintenance` queues sources left in processing states and completes interrupted deletions.
- The admin UI polls the status endpoints while work is pending.

## Consequences

- Upload latency is independent of document size and embedding speed; failures are visible per source and can be
  retried.
- Consistency is guaranteed by PostgreSQL transactions and row locks, without a distributed lock or job framework.
- The queue is per instance and in memory: with several backend instances writes stay safe, but queued work is only
  picked up again by the startup recovery of an instance. A shared queue (database table with `SKIP LOCKED`, or a
  message broker) is the next step if needed.
- Polling adds a few small requests while indexing runs; SSE could replace it later.

## Alternatives considered

- **Synchronous indexing in the upload request**: simplest, but long requests, timeouts behind proxies, and no
  progress reporting.
- **Spring Batch, Quartz or a message broker**: durable distributed jobs, but heavy for the current scale.
- **Deleting old vectors before a reindex**: simpler, but the source would temporarily disappear from answers.
