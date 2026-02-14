CREATE INDEX IF NOT EXISTS idx_source_extraction_jobs_status_locked_at
    ON source_extraction_jobs(status, locked_at);
