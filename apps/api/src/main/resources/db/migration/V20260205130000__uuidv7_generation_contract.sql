-- UUIDv7 generation contract marker.
-- IDs continue to be stored as PostgreSQL UUID columns.
-- Generation strategy is application-side UUIDv7 for:
--   sources, briefings, takeaways, topics, topic_links, enrichments, recalls, users, refresh_sessions.
-- This migration is intentionally forward-only and documents the generation strategy change.

SELECT 1;
