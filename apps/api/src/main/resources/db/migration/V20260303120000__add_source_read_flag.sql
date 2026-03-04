-- Add read flag to sources
ALTER TABLE sources ADD COLUMN is_read BOOLEAN NOT NULL DEFAULT FALSE;
