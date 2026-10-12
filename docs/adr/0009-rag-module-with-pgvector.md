# 0009. Knowledge base as a RAG module behind ports, with PostgreSQL + pgvector

- Status: Accepted
- Date: 2026-10-12

## Context

Administrators need to curate a knowledge base (uploaded documents and manually written text, in collections), and
chat answers should be able to use it with citations. The application already supports several chat providers
(OpenAI, Qwen and Llama on OpenAI-compatible servers) and must stay provider-neutral. Requirements that shape the
design:

- Embeddings must be independent of the chat provider: a local Qwen model may answer with knowledge embedded by
  OpenAI, and the other way round.
- The application must not depend on one vector database; Qdrant, Weaviate, Pinecone, Milvus or OpenSearch may follow.
- Administrators must be able to inspect exactly what is stored (extracted text, chunks, metadata) and delete it
  without leaving vectors behind.
- No additional infrastructure for the first version, and tests must not call real embedding APIs.

## Decision

- The knowledge base is a separate module, `com.example.aichat.rag`, with its own `domain`, `application`,
  `infrastructure` and `api` packages (the hexagonal layering of [ADR 0001](0001-hexagonal-architecture.md)).
- Ports: `DocumentExtractor`, `ChunkingStrategy`, `EmbeddingProvider`, `VectorStore`, `KeywordSearcher`,
  `RerankingService`, `RagContextBuilder`, `KnowledgeAccessPolicy` and repository ports. Adapters: Apache Tika 4 based
  extractors (PDF, DOCX, HTML) plus own extractors for text, Markdown, CSV and JSON; an OpenAI-compatible embedding
  adapter (OpenAI, Ollama, vLLM, LM Studio, LocalAI, llama.cpp) and offline hashing embeddings; `PgVectorStore`;
  PostgreSQL full-text search; a pass-through reranker.
- The chat module defines its own small port, `KnowledgeAugmenter` (question and collections in; instructions,
  delimited excerpts and citations out), implemented by `RagChatAugmenter`. `ChatService` never embeds text or queries
  vectors, and works unchanged when the RAG module is absent.
- Embedding providers are selected by `rag.embedding.provider` through `EmbeddingProviderFactory` beans, independently
  of `llm.providers`.
- The first vector store is **PostgreSQL with pgvector**, in the existing database. Vectors live in their own table
  with the embedding model, dimension and denormalised filter fields (collection, enabled, tags); the column has no
  fixed dimension and an HNSW cosine index is created per dimension. Retrieval only compares vectors of the active
  embedding model.
- Persistence of the RAG module uses `JdbcTemplate` rather than JPA (pgvector types, arrays, JSONB, batches, row
  locks).
- Citations are produced from the retrieved chunks by the server and stored with the answer; the model only refers to
  the numbers it was given. Retrieved text is untrusted: rules in the system message, excerpts delimited in the user
  turn, delimiter look-alikes neutralised.

## Consequences

- One database to operate, back up and secure; transactions cover documents, chunks and vectors together, which makes
  "no orphaned vectors" straightforward.
- Changing the embedding model requires reindexing; the system detects affected sources and offers *Reindex all*.
- Another vector database means implementing `VectorStore` (and `KeywordSearcher` for hybrid search) and a migration
  of the vectors; the rest of the application is unaffected.
- pgvector's HNSW index supports up to 2000 dimensions; larger vectors are searched exactly unless shortened.
- Hybrid retrieval, reranking, query rewriting and new extractors are extension points with defaults today.
- Tests use a deterministic `FakeEmbeddingProvider` and a pgvector Testcontainer.

## Alternatives considered

- **RAG logic inside `ChatService` / `ConversationContextManager`**: fewer classes, but couples chat to storage and
  embedding concerns and makes both harder to test and replace.
- **A dedicated vector database (Qdrant, Weaviate, …) from the start**: better at very large scale, but another
  service to run and no transaction across documents and vectors.
- **Spring AI or LangChain4j abstractions**: useful building blocks, but they bring their own document and vector
  models; the explicit ports keep the domain (sources, versions, collections, citations, admin inspection) under our
  control. Adapters could still use those libraries internally.
- **Embeddings from the selected chat provider**: simpler configuration, but chat models usually cannot embed, and
  switching chat providers would invalidate the index.
