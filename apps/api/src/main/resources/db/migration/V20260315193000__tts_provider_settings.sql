ALTER TABLE user_extraction_settings
    ADD COLUMN IF NOT EXISTS elevenlabs_model_id VARCHAR(100);

UPDATE user_extraction_settings
SET elevenlabs_model_id = 'eleven_flash_v2_5'
WHERE elevenlabs_model_id IS NULL;

ALTER TABLE user_extraction_settings
    ADD COLUMN IF NOT EXISTS inworld_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE user_extraction_settings
    ADD COLUMN IF NOT EXISTS inworld_api_key_encrypted TEXT;

ALTER TABLE user_extraction_settings
    ADD COLUMN IF NOT EXISTS inworld_model_id VARCHAR(100);

UPDATE user_extraction_settings
SET inworld_model_id = 'inworld-tts-1.5-mini'
WHERE inworld_model_id IS NULL;

ALTER TABLE user_extraction_settings
    ADD COLUMN IF NOT EXISTS tts_preferred_provider VARCHAR(30) NOT NULL DEFAULT 'ELEVENLABS';

ALTER TABLE sources
    ADD COLUMN IF NOT EXISTS audio_provider_type VARCHAR(30);

UPDATE sources
SET audio_provider_type = 'ELEVENLABS'
WHERE audio_url IS NOT NULL
  AND audio_provider_type IS NULL;

ALTER TABLE shared_audio_cache
    ADD COLUMN IF NOT EXISTS provider_type VARCHAR(30);

UPDATE shared_audio_cache
SET provider_type = 'ELEVENLABS'
WHERE provider_type IS NULL;

ALTER TABLE shared_audio_cache
    DROP CONSTRAINT IF EXISTS uq_shared_audio_hash_voice_model;

ALTER TABLE shared_audio_cache
    ADD CONSTRAINT uq_shared_audio_hash_provider_voice_model UNIQUE (content_hash, provider_type, voice_id, model_id);
