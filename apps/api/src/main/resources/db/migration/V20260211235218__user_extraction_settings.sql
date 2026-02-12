CREATE TABLE user_extraction_settings (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    firecrawl_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    firecrawl_api_key_encrypted VARCHAR(1024),
    x_api_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    x_api_bearer_token_encrypted VARCHAR(1024),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_user_extraction_settings_user_id
    ON user_extraction_settings(user_id);
