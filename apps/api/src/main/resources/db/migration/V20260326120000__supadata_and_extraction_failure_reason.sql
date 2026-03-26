ALTER TABLE user_extraction_settings ADD COLUMN supadata_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE user_extraction_settings ADD COLUMN supadata_api_key_encrypted TEXT;

ALTER TABLE sources ADD COLUMN extraction_failure_reason VARCHAR(255);
