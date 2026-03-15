ALTER TABLE sources
    ADD COLUMN IF NOT EXISTS narration_state VARCHAR(20) DEFAULT 'NOT_GENERATED';

ALTER TABLE sources
    ADD COLUMN IF NOT EXISTS narration_failure_reason TEXT;

ALTER TABLE sources
    ADD COLUMN IF NOT EXISTS audio_url VARCHAR(2048);

ALTER TABLE sources
    ADD COLUMN IF NOT EXISTS audio_duration_seconds INTEGER;

ALTER TABLE sources
    ADD COLUMN IF NOT EXISTS audio_format VARCHAR(20);

ALTER TABLE sources
    ADD COLUMN IF NOT EXISTS audio_content_hash VARCHAR(64);

ALTER TABLE sources
    ADD COLUMN IF NOT EXISTS audio_generated_at TIMESTAMP;

ALTER TABLE user_extraction_settings
    ADD COLUMN IF NOT EXISTS elevenlabs_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE user_extraction_settings
    ADD COLUMN IF NOT EXISTS elevenlabs_api_key_encrypted TEXT;

CREATE TABLE IF NOT EXISTS shared_audio_cache (
    id UUID PRIMARY KEY,
    content_hash VARCHAR(64) NOT NULL,
    audio_url VARCHAR(2048) NOT NULL,
    duration_seconds INTEGER NOT NULL,
    format VARCHAR(20) NOT NULL DEFAULT 'mp3',
    character_count INTEGER NOT NULL,
    voice_id VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_shared_audio_hash_voice UNIQUE (content_hash, voice_id)
);
