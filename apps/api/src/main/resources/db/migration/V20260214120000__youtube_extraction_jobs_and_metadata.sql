ALTER TABLE sources
    ADD COLUMN IF NOT EXISTS metadata_video_id VARCHAR(50),
    ADD COLUMN IF NOT EXISTS metadata_video_embed_url VARCHAR(2048),
    ADD COLUMN IF NOT EXISTS metadata_video_duration_seconds INTEGER,
    ADD COLUMN IF NOT EXISTS metadata_transcript_source VARCHAR(50),
    ADD COLUMN IF NOT EXISTS metadata_transcript_language VARCHAR(20);

ALTER TABLE shared_source_snapshots
    ADD COLUMN IF NOT EXISTS metadata_video_id VARCHAR(50),
    ADD COLUMN IF NOT EXISTS metadata_video_embed_url VARCHAR(2048),
    ADD COLUMN IF NOT EXISTS metadata_video_duration_seconds INTEGER,
    ADD COLUMN IF NOT EXISTS metadata_transcript_source VARCHAR(50),
    ADD COLUMN IF NOT EXISTS metadata_transcript_language VARCHAR(20);

CREATE TABLE IF NOT EXISTS source_extraction_jobs (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL UNIQUE REFERENCES sources(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    platform VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 5,
    next_attempt_at TIMESTAMP NOT NULL,
    locked_at TIMESTAMP,
    lock_owner VARCHAR(100),
    last_error VARCHAR(4000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_source_extraction_jobs_status_next_attempt
    ON source_extraction_jobs(status, next_attempt_at);
