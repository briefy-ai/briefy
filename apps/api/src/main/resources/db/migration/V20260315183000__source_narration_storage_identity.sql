ALTER TABLE sources
    ADD COLUMN IF NOT EXISTS audio_voice_id VARCHAR(100);

ALTER TABLE sources
    ADD COLUMN IF NOT EXISTS audio_model_id VARCHAR(100);

ALTER TABLE shared_audio_cache
    ADD COLUMN IF NOT EXISTS model_id VARCHAR(100);

ALTER TABLE shared_audio_cache
    DROP CONSTRAINT IF EXISTS uq_shared_audio_hash_voice;

ALTER TABLE shared_audio_cache
    ADD CONSTRAINT uq_shared_audio_hash_voice_model UNIQUE (content_hash, voice_id, model_id);
