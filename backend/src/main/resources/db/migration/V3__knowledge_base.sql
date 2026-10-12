-- Knowledge base for retrieval-augmented generation (RAG).
--
-- knowledge_sources          one row per document or manual text entry (metadata, status, versioning fields)
-- knowledge_source_files     original uploaded bytes (needed to retry or re-extract)
-- knowledge_source_contents  extracted plain text plus structure (pages, sections) used for chunking
-- knowledge_chunks           chunks with citation metadata and a full-text index (hybrid retrieval)
-- knowledge_vectors          embeddings, owned by the pgvector VectorStore adapter
-- rag_executions             one row per retrieval (chat or admin test) with the retrieved chunks
--
-- Every child row cascades from its source, so deleting a source can never leave orphaned chunks or vectors.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE knowledge_collections
(
    id          UUID PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);
CREATE UNIQUE INDEX ux_knowledge_collections_name ON knowledge_collections (LOWER(name));
CREATE UNIQUE INDEX ux_knowledge_collections_single_default ON knowledge_collections (is_default) WHERE is_default;

CREATE TABLE knowledge_sources
(
    id                 UUID PRIMARY KEY,
    type               VARCHAR(16)   NOT NULL,
    title              VARCHAR(300)  NOT NULL,
    original_filename  VARCHAR(255),
    mime_type          VARCHAR(150),
    source_url         VARCHAR(2000),
    collection_id      UUID          NOT NULL REFERENCES knowledge_collections (id) ON DELETE CASCADE,
    status             VARCHAR(16)   NOT NULL,
    enabled            BOOLEAN       NOT NULL DEFAULT TRUE,
    tags               TEXT[]        NOT NULL DEFAULT '{}',
    checksum           VARCHAR(64)   NOT NULL,
    file_size          BIGINT        NOT NULL,
    character_count    INT,
    version            INT           NOT NULL DEFAULT 1,
    parent_source_id   UUID REFERENCES knowledge_sources (id) ON DELETE SET NULL,
    created_by         UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    indexed_at         TIMESTAMPTZ,
    embedding_provider VARCHAR(64),
    embedding_model    VARCHAR(200),
    chunk_count        INT           NOT NULL DEFAULT 0,
    error_message      VARCHAR(1000),
    ingestion_stats    JSONB         NOT NULL DEFAULT '{}',
    CONSTRAINT chk_knowledge_sources_type CHECK (type IN ('FILE', 'TEXT', 'URL')),
    CONSTRAINT chk_knowledge_sources_status CHECK (status IN ('UPLOADED', 'EXTRACTING', 'EXTRACTED', 'CHUNKING',
                                                              'EMBEDDING', 'INDEXED', 'FAILED', 'DELETING'))
);
CREATE INDEX ix_knowledge_sources_collection ON knowledge_sources (collection_id);
CREATE INDEX ix_knowledge_sources_checksum ON knowledge_sources (checksum);
CREATE INDEX ix_knowledge_sources_status ON knowledge_sources (status);
CREATE INDEX ix_knowledge_sources_created ON knowledge_sources (created_at DESC);
CREATE INDEX ix_knowledge_sources_tags ON knowledge_sources USING GIN (tags);

CREATE TABLE knowledge_source_files
(
    source_id  UUID PRIMARY KEY REFERENCES knowledge_sources (id) ON DELETE CASCADE,
    content    BYTEA       NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE knowledge_source_contents
(
    source_id       UUID PRIMARY KEY REFERENCES knowledge_sources (id) ON DELETE CASCADE,
    content         TEXT        NOT NULL,
    structure       JSONB       NOT NULL DEFAULT '{}',
    character_count INT         NOT NULL,
    extracted_at    TIMESTAMPTZ NOT NULL
);

CREATE TABLE knowledge_chunks
(
    id              UUID PRIMARY KEY,
    source_id       UUID        NOT NULL REFERENCES knowledge_sources (id) ON DELETE CASCADE,
    collection_id   UUID        NOT NULL,
    chunk_index     INT         NOT NULL,
    content         TEXT        NOT NULL,
    token_count     INT         NOT NULL,
    character_start INT         NOT NULL,
    character_end   INT         NOT NULL,
    page_number     INT,
    section_title   VARCHAR(300),
    metadata        JSONB       NOT NULL DEFAULT '{}',
    content_tsv     TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT ux_knowledge_chunks_source_index UNIQUE (source_id, chunk_index)
);
CREATE INDEX ix_knowledge_chunks_tsv ON knowledge_chunks USING GIN (content_tsv);

-- Embeddings live in their own table so that the vector store stays replaceable. "vector" without a fixed
-- dimension allows switching embedding models; the adapter creates an HNSW index per active dimension.
CREATE TABLE knowledge_vectors
(
    chunk_id        UUID PRIMARY KEY REFERENCES knowledge_chunks (id) ON DELETE CASCADE,
    source_id       UUID         NOT NULL REFERENCES knowledge_sources (id) ON DELETE CASCADE,
    collection_id   UUID         NOT NULL,
    embedding_model VARCHAR(200) NOT NULL,
    dimensions      INT          NOT NULL,
    embedding       VECTOR       NOT NULL,
    source_enabled  BOOLEAN      NOT NULL,
    tags            TEXT[]       NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_knowledge_vectors_source ON knowledge_vectors (source_id);
CREATE INDEX ix_knowledge_vectors_filter ON knowledge_vectors (embedding_model, dimensions, collection_id)
    WHERE source_enabled;

CREATE TABLE rag_executions
(
    id                    UUID PRIMARY KEY,
    purpose               VARCHAR(16)  NOT NULL,
    conversation_id       UUID REFERENCES conversations (id) ON DELETE SET NULL,
    message_id            UUID REFERENCES messages (id) ON DELETE SET NULL,
    user_id               UUID REFERENCES users (id) ON DELETE SET NULL,
    query                 TEXT         NOT NULL,
    collection_ids        UUID[]       NOT NULL DEFAULT '{}',
    embedding_provider    VARCHAR(64),
    embedding_model       VARCHAR(200),
    retrieval_mode        VARCHAR(16)  NOT NULL,
    retrieved_chunk_count INT          NOT NULL,
    context_chunk_count   INT          NOT NULL,
    total_context_tokens  INT          NOT NULL,
    embedding_duration_ms BIGINT,
    retrieval_duration_ms BIGINT       NOT NULL,
    status                VARCHAR(16)  NOT NULL,
    error_message         VARCHAR(500),
    created_at            TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_rag_executions_purpose CHECK (purpose IN ('CHAT', 'TEST')),
    CONSTRAINT chk_rag_executions_status CHECK (status IN ('SUCCESS', 'EMPTY', 'ERROR'))
);
CREATE INDEX ix_rag_executions_message ON rag_executions (message_id);
CREATE INDEX ix_rag_executions_created ON rag_executions (created_at);

CREATE TABLE rag_retrieved_chunks
(
    rag_execution_id UUID         NOT NULL REFERENCES rag_executions (id) ON DELETE CASCADE,
    rank             INT          NOT NULL,
    chunk_id         UUID REFERENCES knowledge_chunks (id) ON DELETE SET NULL,
    source_id        UUID REFERENCES knowledge_sources (id) ON DELETE SET NULL,
    source_title     VARCHAR(300) NOT NULL,
    collection_name  VARCHAR(100),
    chunk_index      INT,
    page_number      INT,
    section_title    VARCHAR(300),
    similarity_score DOUBLE PRECISION,
    keyword_score    DOUBLE PRECISION,
    reranker_score   DOUBLE PRECISION,
    citation_index   INT,
    PRIMARY KEY (rag_execution_id, rank)
);

-- Per-conversation knowledge settings (RAG on/off and the selected collections; empty = all enabled collections).
ALTER TABLE conversations
    ADD COLUMN knowledge_enabled        BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN knowledge_collection_ids UUID[]  NOT NULL DEFAULT '{}';

-- Answers generated from the admin RAG test console are recorded like any other LLM call.
ALTER TABLE llm_executions DROP CONSTRAINT chk_llm_executions_purpose;
ALTER TABLE llm_executions
    ADD CONSTRAINT chk_llm_executions_purpose CHECK (purpose IN ('CHAT', 'COMPARE', 'TITLE', 'SUMMARY', 'RAG_TEST'));

INSERT INTO knowledge_collections (id, name, description, enabled, is_default, created_at, updated_at)
VALUES ('6a1d0c55-7b0e-4c35-9f1e-000000000001', 'General', 'Default collection for knowledge that has no more '
            || 'specific home.', TRUE, TRUE, NOW(), NOW());
