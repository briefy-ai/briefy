-- Source full schema
-- Adds all fields to sources table for URL ingestion feature

-- Add URL fields
ALTER TABLE sources ADD COLUMN url_raw VARCHAR(2048) NOT NULL;
ALTER TABLE sources ADD COLUMN url_normalized VARCHAR(2048) NOT NULL;
ALTER TABLE sources ADD COLUMN url_platform VARCHAR(50) NOT NULL DEFAULT 'web';

-- Add status field (enum stored as string)
ALTER TABLE sources ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'submitted';

-- Add content fields
ALTER TABLE sources ADD COLUMN content_text TEXT;
ALTER TABLE sources ADD COLUMN content_word_count INTEGER;

-- Add metadata fields
ALTER TABLE sources ADD COLUMN metadata_title VARCHAR(500);
ALTER TABLE sources ADD COLUMN metadata_author VARCHAR(255);
ALTER TABLE sources ADD COLUMN metadata_published_date TIMESTAMP;
ALTER TABLE sources ADD COLUMN metadata_platform VARCHAR(50);
ALTER TABLE sources ADD COLUMN metadata_estimated_reading_time INTEGER;

-- Add ownership and timestamps
ALTER TABLE sources ADD COLUMN owner_id UUID;
ALTER TABLE sources ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE sources ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- Create unique index for URL deduplication
CREATE UNIQUE INDEX idx_sources_url_normalized ON sources(url_normalized);

-- Create index for status filtering
CREATE INDEX idx_sources_status ON sources(status);

-- Create index for owner filtering
CREATE INDEX idx_sources_owner_id ON sources(owner_id);
