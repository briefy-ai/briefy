ALTER TABLE sources
    ADD COLUMN IF NOT EXISTS topic_extraction_state VARCHAR(30) DEFAULT 'PENDING';

ALTER TABLE sources
    ADD COLUMN IF NOT EXISTS topic_extraction_failure_reason VARCHAR(255);

UPDATE sources
SET topic_extraction_state = 'SUCCEEDED',
    topic_extraction_failure_reason = NULL
WHERE status = 'ACTIVE';
