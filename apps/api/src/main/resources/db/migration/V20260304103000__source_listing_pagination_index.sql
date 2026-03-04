CREATE INDEX IF NOT EXISTS idx_sources_user_status_updated_at_id
    ON sources(user_id, status, updated_at DESC, id DESC);
