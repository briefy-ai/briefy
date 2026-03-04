CREATE TABLE share_links (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    token       VARCHAR(64)   NOT NULL UNIQUE,
    entity_type VARCHAR(32)   NOT NULL,
    entity_id   UUID          NOT NULL,
    user_id     UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX share_links_token_idx       ON share_links(token);
CREATE INDEX share_links_user_entity_idx ON share_links(user_id, entity_type, entity_id);
