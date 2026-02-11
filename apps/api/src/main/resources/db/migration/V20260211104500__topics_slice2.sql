-- Slice 2: Topic suggestion lifecycle and topic-source graph links.
-- This migration upgrades placeholder Topic/TopicLink tables to support:
-- - Topic status lifecycle: SUGGESTED -> ACTIVE -> ARCHIVED
-- - TopicLink status lifecycle: SUGGESTED -> ACTIVE -> REMOVED
-- - Source-scoped pending suggestions and confirmation/dismissal flows

ALTER TABLE topics
    ADD COLUMN name VARCHAR(200),
    ADD COLUMN name_normalized VARCHAR(200),
    ADD COLUMN status VARCHAR(20),
    ADD COLUMN origin VARCHAR(20),
    ADD COLUMN created_at TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP;

UPDATE topics
SET name = CONCAT('legacy-topic-', id::text),
    name_normalized = CONCAT('legacy-topic-', id::text),
    status = 'ACTIVE',
    origin = 'SYSTEM',
    created_at = COALESCE(created_at, CURRENT_TIMESTAMP),
    updated_at = COALESCE(updated_at, CURRENT_TIMESTAMP)
WHERE name IS NULL;

ALTER TABLE topics
    ALTER COLUMN name SET NOT NULL,
    ALTER COLUMN name_normalized SET NOT NULL,
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN origin SET NOT NULL,
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN status SET DEFAULT 'SUGGESTED',
    ALTER COLUMN origin SET DEFAULT 'SYSTEM',
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;

CREATE UNIQUE INDEX uq_topics_user_id_name_normalized
    ON topics(user_id, name_normalized);
CREATE INDEX idx_topics_user_id_status
    ON topics(user_id, status);
CREATE INDEX idx_topics_user_id_created_at
    ON topics(user_id, created_at DESC);

ALTER TABLE topic_links
    ADD COLUMN topic_id UUID,
    ADD COLUMN target_type VARCHAR(20),
    ADD COLUMN target_id UUID,
    ADD COLUMN assignment_method VARCHAR(30),
    ADD COLUMN status VARCHAR(20),
    ADD COLUMN suggestion_confidence NUMERIC(5,4),
    ADD COLUMN assigned_at TIMESTAMP,
    ADD COLUMN removed_at TIMESTAMP,
    ADD COLUMN created_at TIMESTAMP,
    ADD COLUMN updated_at TIMESTAMP;

UPDATE topic_links
SET topic_id = (
        SELECT t.id
        FROM topics t
        WHERE t.user_id = topic_links.user_id
        ORDER BY t.created_at
        LIMIT 1
    ),
    target_type = 'SOURCE',
    target_id = (
        SELECT s.id
        FROM sources s
        WHERE s.user_id = topic_links.user_id
        ORDER BY s.created_at
        LIMIT 1
    ),
    assignment_method = 'SYSTEM_SUGGESTED',
    status = 'REMOVED',
    assigned_at = COALESCE(assigned_at, CURRENT_TIMESTAMP),
    created_at = COALESCE(created_at, CURRENT_TIMESTAMP),
    updated_at = COALESCE(updated_at, CURRENT_TIMESTAMP)
WHERE topic_id IS NULL;

DELETE FROM topic_links
WHERE topic_id IS NULL OR target_id IS NULL;

ALTER TABLE topic_links
    ALTER COLUMN topic_id SET NOT NULL,
    ALTER COLUMN target_type SET NOT NULL,
    ALTER COLUMN target_id SET NOT NULL,
    ALTER COLUMN assignment_method SET NOT NULL,
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN assigned_at SET NOT NULL,
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN target_type SET DEFAULT 'SOURCE',
    ALTER COLUMN assignment_method SET DEFAULT 'SYSTEM_SUGGESTED',
    ALTER COLUMN status SET DEFAULT 'SUGGESTED',
    ALTER COLUMN assigned_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE topic_links
    ADD CONSTRAINT fk_topic_links_topic_id FOREIGN KEY (topic_id) REFERENCES topics(id) ON DELETE CASCADE;

-- Ensure there can be at most one live edge (SUGGESTED/ACTIVE) per
-- (user, topic, target) tuple.
CREATE UNIQUE INDEX uq_topic_links_live_edge
    ON topic_links(user_id, topic_id, target_type, target_id)
    WHERE status IN ('SUGGESTED', 'ACTIVE');

CREATE INDEX idx_topic_links_source_suggestions
    ON topic_links(user_id, target_type, target_id, status, assigned_at DESC);
CREATE INDEX idx_topic_links_topic_status
    ON topic_links(user_id, topic_id, status);
