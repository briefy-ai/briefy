ALTER TABLE sources
    ADD COLUMN IF NOT EXISTS metadata_extraction_provider VARCHAR(50);

ALTER TABLE shared_source_snapshots
    ADD COLUMN IF NOT EXISTS metadata_extraction_provider VARCHAR(50);
