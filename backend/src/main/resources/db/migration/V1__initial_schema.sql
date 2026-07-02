-- Core schema of the AI Workspace.
-- Every assistant message records the provider/model that produced it, so a conversation can mix providers.

CREATE TABLE users
(
    id                    UUID PRIMARY KEY,
    username              VARCHAR(64)  NOT NULL,
    email                 VARCHAR(255) NOT NULL,
    display_name          VARCHAR(128),
    password_hash         VARCHAR(255) NOT NULL,
    role                  VARCHAR(16)  NOT NULL,
    status                VARCHAR(16)  NOT NULL,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    last_login_at         TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED'))
);
CREATE UNIQUE INDEX ux_users_username ON users (LOWER(username));
CREATE UNIQUE INDEX ux_users_email ON users (LOWER(email));

CREATE TABLE system_prompts
(
    id          UUID PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    content     TEXT         NOT NULL,
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);
CREATE UNIQUE INDEX ux_system_prompts_name ON system_prompts (LOWER(name));
CREATE UNIQUE INDEX ux_system_prompts_single_default ON system_prompts (is_default) WHERE is_default;

CREATE TABLE conversations
(
    id               UUID PRIMARY KEY,
    user_id          UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title            VARCHAR(200) NOT NULL,
    title_source     VARCHAR(16)  NOT NULL,
    system_prompt_id UUID         REFERENCES system_prompts (id) ON DELETE SET NULL,
    last_provider    VARCHAR(64),
    last_model       VARCHAR(200),
    summary          TEXT,
    summarized_until TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_conversations_title_source CHECK (title_source IN ('DEFAULT', 'GENERATED', 'USER'))
);
CREATE INDEX ix_conversations_user_updated ON conversations (user_id, updated_at DESC);

CREATE TABLE messages
(
    id              UUID PRIMARY KEY,
    conversation_id UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    role            VARCHAR(16) NOT NULL,
    content         TEXT        NOT NULL,
    reasoning       TEXT,
    provider        VARCHAR(64),
    model           VARCHAR(200),
    status          VARCHAR(16) NOT NULL,
    error_code      VARCHAR(64),
    error_message   VARCHAR(1000),
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_messages_role CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT chk_messages_status CHECK (status IN ('STREAMING', 'COMPLETE', 'CANCELLED', 'ERROR'))
);
CREATE INDEX ix_messages_conversation_created ON messages (conversation_id, created_at, id);
CREATE INDEX ix_messages_streaming ON messages (updated_at) WHERE status = 'STREAMING';

-- One row per LLM invocation (chat, compare, title, summary). Survives deletion of messages, conversations and
-- users so that usage statistics stay accurate.
CREATE TABLE llm_executions
(
    id                     UUID PRIMARY KEY,
    message_id             UUID REFERENCES messages (id) ON DELETE SET NULL,
    conversation_id        UUID REFERENCES conversations (id) ON DELETE SET NULL,
    user_id                UUID REFERENCES users (id) ON DELETE SET NULL,
    provider               VARCHAR(64)  NOT NULL,
    model                  VARCHAR(200) NOT NULL,
    provider_kind          VARCHAR(16)  NOT NULL,
    purpose                VARCHAR(16)  NOT NULL,
    input_tokens           INT,
    output_tokens          INT,
    latency_ms             BIGINT       NOT NULL,
    time_to_first_token_ms BIGINT,
    estimated_cost         NUMERIC(14, 6),
    status                 VARCHAR(16)  NOT NULL,
    error_code             VARCHAR(64),
    created_at             TIMESTAMPTZ  NOT NULL,
    CONSTRAINT chk_llm_executions_kind CHECK (provider_kind IN ('CLOUD', 'LOCAL')),
    CONSTRAINT chk_llm_executions_purpose CHECK (purpose IN ('CHAT', 'COMPARE', 'TITLE', 'SUMMARY')),
    CONSTRAINT chk_llm_executions_status CHECK (status IN ('SUCCESS', 'ERROR', 'CANCELLED'))
);
CREATE INDEX ix_llm_executions_created ON llm_executions (created_at);
CREATE INDEX ix_llm_executions_provider_created ON llm_executions (provider, created_at);
CREATE INDEX ix_llm_executions_message ON llm_executions (message_id);

-- Administrator overrides of provider defaults from configuration. NULL = use the configured value.
CREATE TABLE provider_settings
(
    provider_id     VARCHAR(64) PRIMARY KEY,
    enabled         BOOLEAN,
    default_model   VARCHAR(200),
    timeout_seconds INT,
    temperature     DOUBLE PRECISION,
    max_tokens      INT,
    updated_at      TIMESTAMPTZ NOT NULL,
    updated_by      VARCHAR(64)
);
