ALTER TABLE sources
    ADD COLUMN IF NOT EXISTS metadata_og_image_url VARCHAR(2048);

ALTER TABLE shared_source_snapshots
    ADD COLUMN IF NOT EXISTS metadata_og_image_url VARCHAR(2048);
