ALTER TABLE sources
    ADD COLUMN IF NOT EXISTS metadata_formatting_state VARCHAR(30) DEFAULT 'PENDING';

ALTER TABLE sources
    ADD COLUMN IF NOT EXISTS metadata_formatting_failure_reason VARCHAR(255);

ALTER TABLE shared_source_snapshots
    ADD COLUMN IF NOT EXISTS metadata_formatting_state VARCHAR(30) DEFAULT 'PENDING';

ALTER TABLE shared_source_snapshots
    ADD COLUMN IF NOT EXISTS metadata_formatting_failure_reason VARCHAR(255);

UPDATE sources
SET metadata_formatting_state = CASE
    WHEN COALESCE(metadata_ai_formatted, FALSE) THEN 'SUCCEEDED'
    ELSE 'PENDING'
END
WHERE content_text IS NOT NULL;

UPDATE shared_source_snapshots
SET metadata_formatting_state = CASE
    WHEN COALESCE(metadata_ai_formatted, FALSE) THEN 'SUCCEEDED'
    ELSE 'PENDING'
END
WHERE content_text IS NOT NULL;
