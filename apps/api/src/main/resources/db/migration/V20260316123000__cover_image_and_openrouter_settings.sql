ALTER TABLE user_extraction_settings ADD COLUMN openrouter_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE user_extraction_settings ADD COLUMN openrouter_api_key_encrypted TEXT;
ALTER TABLE user_extraction_settings ADD COLUMN openrouter_image_model VARCHAR(100);

ALTER TABLE sources ADD COLUMN cover_image_key VARCHAR(512);
ALTER TABLE sources ADD COLUMN featured_image_key VARCHAR(512);
