CREATE TABLE oauth_clients (
    id UUID PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    allowed_redirect_uris TEXT NOT NULL,
    allowed_scopes TEXT NOT NULL,
    require_pkce BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE oauth_authorization_codes (
    id UUID PRIMARY KEY,
    code_hash VARCHAR(255) NOT NULL UNIQUE,
    client_id VARCHAR(100) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id),
    scopes TEXT NOT NULL,
    code_challenge VARCHAR(255) NOT NULL,
    redirect_uri TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ
);

CREATE INDEX oauth_authorization_codes_expires_at_idx ON oauth_authorization_codes (expires_at);

CREATE TABLE oauth_access_grants (
    id UUID PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id),
    scopes TEXT NOT NULL,
    refresh_token_hash VARCHAR(255) NOT NULL UNIQUE,
    issued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX oauth_access_grants_user_id_idx ON oauth_access_grants (user_id);
CREATE INDEX oauth_access_grants_client_id_user_id_idx ON oauth_access_grants (client_id, user_id);

INSERT INTO oauth_clients (id, client_id, name, allowed_redirect_uris, allowed_scopes, require_pkce, created_at)
VALUES (
    'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    'espriu',
    'Espriu',
    'https://espriu.app/oauth/callback,http://localhost:3001/oauth/callback',
    'mcp:read',
    TRUE,
    CURRENT_TIMESTAMP
);
