CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_sources_metadata_title_trgm
    ON sources USING gin (metadata_title gin_trgm_ops);
