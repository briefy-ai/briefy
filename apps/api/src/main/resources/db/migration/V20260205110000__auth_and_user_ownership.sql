-- Authentication and ownership hardening
-- Current development data is intentionally discarded for this migration.

TRUNCATE TABLE sources, briefings, takeaways, topics, topic_links, enrichments, recalls;

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    provider_subject VARCHAR(255),
    display_name VARCHAR(120),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);

CREATE TABLE refresh_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_refresh_sessions_user_revoked_expires
    ON refresh_sessions(user_id, revoked_at, expires_at);

ALTER TABLE sources RENAME COLUMN owner_id TO user_id;
ALTER TABLE sources ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE sources
    ADD CONSTRAINT fk_sources_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

DROP INDEX IF EXISTS idx_sources_owner_id;
DROP INDEX IF EXISTS idx_sources_url_normalized;
CREATE INDEX idx_sources_user_id ON sources(user_id);
CREATE UNIQUE INDEX idx_sources_user_id_url_normalized ON sources(user_id, url_normalized);

ALTER TABLE briefings ADD COLUMN user_id UUID NOT NULL;
ALTER TABLE briefings
    ADD CONSTRAINT fk_briefings_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
CREATE INDEX idx_briefings_user_id ON briefings(user_id);

ALTER TABLE takeaways ADD COLUMN user_id UUID NOT NULL;
ALTER TABLE takeaways
    ADD CONSTRAINT fk_takeaways_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
CREATE INDEX idx_takeaways_user_id ON takeaways(user_id);

ALTER TABLE topics ADD COLUMN user_id UUID NOT NULL;
ALTER TABLE topics
    ADD CONSTRAINT fk_topics_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
CREATE INDEX idx_topics_user_id ON topics(user_id);

ALTER TABLE topic_links ADD COLUMN user_id UUID NOT NULL;
ALTER TABLE topic_links
    ADD CONSTRAINT fk_topic_links_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
CREATE INDEX idx_topic_links_user_id ON topic_links(user_id);

ALTER TABLE enrichments ADD COLUMN user_id UUID NOT NULL;
ALTER TABLE enrichments
    ADD CONSTRAINT fk_enrichments_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
CREATE INDEX idx_enrichments_user_id ON enrichments(user_id);

ALTER TABLE recalls ADD COLUMN user_id UUID NOT NULL;
ALTER TABLE recalls
    ADD CONSTRAINT fk_recalls_user_id FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
CREATE INDEX idx_recalls_user_id ON recalls(user_id);
