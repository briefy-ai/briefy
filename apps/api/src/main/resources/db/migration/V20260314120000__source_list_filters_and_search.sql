CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_sources_metadata_title_trgm
    ON sources USING gin (metadata_title gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_topic_links_target_active
    ON topic_links(target_id, status)
    WHERE target_type = 'SOURCE' AND status = 'ACTIVE';
