CREATE TABLE user_ai_settings (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    topic_extraction_provider VARCHAR(50) NOT NULL,
    topic_extraction_model VARCHAR(100) NOT NULL,
    source_formatting_provider VARCHAR(50) NOT NULL,
    source_formatting_model VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX idx_user_ai_settings_user_id
    ON user_ai_settings(user_id);
