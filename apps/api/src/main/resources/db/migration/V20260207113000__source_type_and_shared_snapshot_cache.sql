ALTER TABLE sources
    ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'BLOG';

CREATE TABLE shared_source_snapshots (
    id UUID PRIMARY KEY,
    url_normalized VARCHAR(2048) NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    content_text TEXT,
    content_word_count INTEGER,
    metadata_title VARCHAR(500),
    metadata_author VARCHAR(255),
    metadata_published_date TIMESTAMP,
    metadata_platform VARCHAR(50),
    metadata_estimated_reading_time INTEGER,
    fetched_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    version INTEGER NOT NULL,
    is_latest BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_shared_source_snapshot_url_version UNIQUE (url_normalized, version)
);

CREATE INDEX idx_shared_source_snapshots_url_latest
    ON shared_source_snapshots(url_normalized, is_latest);

CREATE INDEX idx_shared_source_snapshots_expires_at
    ON shared_source_snapshots(expires_at);
