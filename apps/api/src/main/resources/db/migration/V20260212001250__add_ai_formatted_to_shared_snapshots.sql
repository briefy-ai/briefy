ALTER TABLE shared_source_snapshots
    ADD COLUMN IF NOT EXISTS metadata_ai_formatted BOOLEAN DEFAULT FALSE;
