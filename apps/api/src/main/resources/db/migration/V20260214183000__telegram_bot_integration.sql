CREATE TABLE telegram_links (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    telegram_user_id BIGINT NOT NULL,
    telegram_chat_id BIGINT NOT NULL,
    telegram_username VARCHAR(255),
    linked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_telegram_links_user_id
    ON telegram_links(user_id);

CREATE UNIQUE INDEX idx_telegram_links_telegram_user_id
    ON telegram_links(telegram_user_id);

CREATE UNIQUE INDEX idx_telegram_links_telegram_chat_id
    ON telegram_links(telegram_chat_id);

CREATE TABLE telegram_link_codes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    used_at TIMESTAMP
);

CREATE UNIQUE INDEX idx_telegram_link_codes_code_hash
    ON telegram_link_codes(code_hash);

CREATE UNIQUE INDEX idx_telegram_link_codes_user_active
    ON telegram_link_codes(user_id)
    WHERE used_at IS NULL;

CREATE TABLE telegram_ingestion_jobs (
    id UUID PRIMARY KEY,
    telegram_chat_id BIGINT NOT NULL,
    telegram_message_id BIGINT NOT NULL,
    telegram_user_id BIGINT NOT NULL,
    linked_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    payload_text TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    next_attempt_at TIMESTAMP NOT NULL,
    locked_at TIMESTAMP,
    lock_owner VARCHAR(100),
    last_error VARCHAR(4000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_telegram_ingestion_jobs_message
    ON telegram_ingestion_jobs(telegram_chat_id, telegram_message_id);

CREATE INDEX idx_telegram_ingestion_jobs_status_due
    ON telegram_ingestion_jobs(status, next_attempt_at);
