---
name: knowledge-base
description: Configure, verify, tune and extend the RAG knowledge base of AI Workspace — embedding models, similarity threshold, ingestion checks, and adding file types, embedding backends, vector stores, rerankers or collection permissions. Use this for work under backend `rag/`, the `/admin/knowledge` UI, or when indexing or knowledge-based answers misbehave.
---

# Knowledge base (RAG)

The knowledge base is the `com.example.aichat.rag` module (ports and adapters). Read first:
[README "Knowledge base (RAG)"](../../../README.md#knowledge-base-rag),
[architecture section 8](../../../docs/architecture.md#8-knowledge-base-rag),
[ADR 0009](../../../docs/adr/0009-rag-module-with-pgvector.md) and
[ADR 0010](../../../docs/adr/0010-asynchronous-ingestion-and-index-consistency.md).

Non-negotiable (root AGENTS.md, invariant 12): chat uses only the `KnowledgeAugmenter` port; pgvector, Tika and
embedding-server types stay in `rag/infrastructure`; every change that affects retrieval updates chunks and vectors in
the same transaction; citations come from retrieved chunks; tests never call real embedding APIs.

## Configure the embedding model

Embeddings are independent of the chat providers. Set them in `.env`, then apply with
`scripts/deploy.sh up --no-build` (or restart `scripts/run-dev.sh`):

```bash
# OpenAI (uses OPENAI_API_KEY unless RAG_EMBEDDING_API_KEY is set)
RAG_EMBEDDING_PROVIDER=openai
RAG_EMBEDDING_MODEL=text-embedding-3-small

# Ollama on the host (ollama pull qwen3-embedding:0.6b); any OpenAI-compatible /v1/embeddings server works
RAG_EMBEDDING_PROVIDER=ollama
RAG_EMBEDDING_BASE_URL=http://host.docker.internal:11434/v1
RAG_EMBEDDING_MODEL=qwen3-embedding:0.6b
RAG_EMBEDDING_QUERY_PREFIX=Instruct: Given a question, retrieve passages that answer it\nQuery:

# No model at all (lexical feature hashing)
RAG_EMBEDDING_PROVIDER=hashing
RAG_SIMILARITY_THRESHOLD=0.12
```

Check the embedding server from the host before switching:

```bash
curl -s http://localhost:11434/v1/embeddings -H 'Content-Type: application/json' \
  -d '{"model":"qwen3-embedding:0.6b","input":["hello"]}' | python3 -c 'import json,sys; print(len(json.load(sys.stdin)["data"][0]["embedding"]))'
scripts/status.sh            # "Embeddings" and "Knowledge base" lines
```

After a model change, existing sources show *needs reindex*: click **Reindex all** in Admin → Knowledge base
(`POST /api/admin/knowledge/reindex-stale`). Until then they are not searchable.

## Calibrate retrieval

The similarity threshold depends on the embedding model (default 0.70; offline hashing needs about 0.12).

1. Index a few real documents. Open Admin → Knowledge base → **Test retrieval**.
2. Set *Similarity threshold* to `0` and *Top K* to `20`. Ask questions whose answer you know, and some unrelated
   questions. Note the similarity of the right chunks and of the best unrelated chunk.
3. Set `RAG_SIMILARITY_THRESHOLD` between the two, redeploy, and repeat the questions with the default threshold.
4. Exact codes, names or rare terms are missed? Try `RAG_RETRIEVAL_MODE=HYBRID`. Citations too coarse? Lower
   `RAG_CHUNK_SIZE` (for example 400) and reindex.
5. *Generate answer* shows the exact prompt, context size and citations for a chosen model.

## Verify end to end

```bash
API=http://localhost:8088
ADMIN_PASSWORD=$(grep '^ADMIN_PASSWORD=' .env | cut -d= -f2-)
TOKEN=$(curl -s -X POST $API/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"login\":\"admin\",\"password\":\"$ADMIN_PASSWORD\"}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["accessToken"])')
AUTH="Authorization: Bearer $TOKEN"

ID=$(curl -s -X POST $API/api/admin/knowledge/files -H "$AUTH" -F file=@README.md -F tags=docs \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["sourceId"])')
curl -s $API/api/admin/knowledge/$ID/status -H "$AUTH"                 # repeat until INDEXED (or FAILED + reason)
curl -s -X POST $API/api/admin/knowledge/search -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"query":"How do I start the stack?","similarityThreshold":0}'
curl -s -X DELETE $API/api/admin/knowledge/$ID -H "$AUTH"               # deletedChunks == deletedEmbeddings
docker compose exec postgres psql -U aichat -d aichat -Atc \
  "select count(*) from knowledge_vectors where source_id = '$ID'"       # must be 0
```

Uploading the same file twice must answer `409 DUPLICATE_DOCUMENT`; resend with `-F duplicateStrategy=NEW_VERSION` or
`REPLACE`.

## Extend

**A new file type.** Add a `DocumentExtractor` bean in `rag/infrastructure/extractor` (`name()`,
`supports(contentType, filename)`, `extract(input, metadata)` returning text plus pages/sections via
`StructuredTextBuilder`), give it an `@Order`, and add the type to `DocumentType` (label, MIME type, extensions,
accepted sniffed content types). Add the extension to `ACCEPTED_FILES` in `frontend/src/components/knowledge/AddDocumentDialog.tsx`.
Cover it in `DocumentExtractorsTest` and `UploadValidatorTest`.

**A new embedding backend** (for example a native Bedrock or Cohere API). Implement `EmbeddingProvider`
(`providerId()`, `modelId()`, `dimensions()`, `embed(texts)`; throw `EmbeddingException` with `retryable` set
correctly) and an `EmbeddingProviderFactory` bean whose `types()` contain the new `rag.embedding.provider` value.
OpenAI-compatible servers need no code: use `openai-compatible` (or an alias) with a base URL. Test against WireMock
like `OpenAiCompatibleEmbeddingProviderTest`.

**Another vector store** (Qdrant, Weaviate, Pinecone, Milvus, OpenSearch). Implement `VectorStore` (`store`,
`search` with `VectorFilter` for collections, sources, tags and the enabled flag, `deleteBySourceId`,
`deleteByCollectionId`, `updateSourceMetadata`, `countBySourceId`, optional `prepare`) and, for hybrid retrieval,
`KeywordSearcher`. Keep deletes and metadata updates consistent with the database transaction (or make them
idempotent and retried), and migrate existing vectors by reindexing.

**A reranker.** Implement `RerankingService` (`name()`, `rerank(query, chunks)`, set `rerankScore`), register it as a
bean, and select it with `rag.reranking.enabled=true` and `rag.reranking.provider=<name>`.

**Collection-level permissions.** Register a `KnowledgeAccessPolicy` bean (`canRead(userId, collection)`); it replaces
the default policy and is applied to chat retrieval, the collection picker and citation excerpts. Admin endpoints are
not affected.

## Tests

```bash
scripts/test.sh backend --test 'KnowledgeBaseIntegrationTest,RagChatIntegrationTest,DocumentExtractorsTest,UploadValidatorTest'
scripts/test.sh backend --test 'RecursiveTextChunkingStrategyTest,DefaultRetrievalServiceTest,DefaultRagContextBuilderTest,RagChatAugmenterTest'
cd frontend && npx vitest run src/pages/Knowledge src/components/knowledge src/components/chat
```

- Embeddings in tests come from `FakeEmbeddingProvider` (`rag.embedding.provider: fake`); never call real APIs.
- Ingestion is asynchronous: poll the status endpoint (`awaitStatus`, `awaitIndexedAfter`) instead of sleeping.
- Integration tests share one database: give documents unique content (a random marker) so that duplicate detection
  does not interfere.

## Troubleshooting

See [RAG troubleshooting](../../../README.md#rag-troubleshooting). The most common causes: the embedding server is
not reachable from the backend container (use `host.docker.internal`), the embedding model is not pulled, the
threshold is too high for the model, sources are disabled or need a reindex, or a scanned PDF has no text layer.
