CREATE TABLE IF NOT EXISTS source_annotations (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body TEXT NOT NULL,
    anchor_quote TEXT NOT NULL,
    anchor_prefix TEXT NOT NULL DEFAULT '',
    anchor_suffix TEXT NOT NULL DEFAULT '',
    anchor_start INTEGER NOT NULL,
    anchor_end INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    archived_cause VARCHAR(30),
    archived_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_source_annotations_anchor_range CHECK (anchor_start >= 0 AND anchor_end > anchor_start)
);

CREATE INDEX IF NOT EXISTS idx_source_annotations_source_user_status_anchor
    ON source_annotations(source_id, user_id, status, anchor_start, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_source_annotations_user_status_updated
    ON source_annotations(user_id, status, updated_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uq_source_annotations_active_anchor
    ON source_annotations(source_id, user_id, anchor_start, anchor_end)
    WHERE status = 'ACTIVE';
