CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE source_embeddings (
    source_id UUID PRIMARY KEY REFERENCES sources(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    embedding vector(1536) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_source_embeddings_user_id
    ON source_embeddings(user_id);

CREATE INDEX idx_source_embeddings_embedding_cosine
    ON source_embeddings USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
