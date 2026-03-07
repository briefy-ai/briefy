ALTER TABLE user_ai_settings
    ADD COLUMN IF NOT EXISTS briefing_subagent_execution_provider VARCHAR(50);

ALTER TABLE user_ai_settings
    ADD COLUMN IF NOT EXISTS briefing_subagent_execution_model VARCHAR(100);

ALTER TABLE user_ai_settings
    ADD COLUMN IF NOT EXISTS briefing_synthesis_provider VARCHAR(50);

ALTER TABLE user_ai_settings
    ADD COLUMN IF NOT EXISTS briefing_synthesis_model VARCHAR(100);
