ALTER TABLE users ADD COLUMN onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE;

-- Existing users skip the wizard
UPDATE users SET onboarding_completed = TRUE;
