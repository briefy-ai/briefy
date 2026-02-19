ALTER TABLE briefings
    ADD COLUMN IF NOT EXISTS enrichment_intent VARCHAR(40) NOT NULL DEFAULT 'DEEP_DIVE',
    ADD COLUMN IF NOT EXISTS status VARCHAR(40) NOT NULL DEFAULT 'PLAN_PENDING_APPROVAL',
    ADD COLUMN IF NOT EXISTS content_markdown TEXT,
    ADD COLUMN IF NOT EXISTS citations_json TEXT,
    ADD COLUMN IF NOT EXISTS conflict_highlights_json TEXT,
    ADD COLUMN IF NOT EXISTS error_json TEXT,
    ADD COLUMN IF NOT EXISTS planned_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS generation_started_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS generation_completed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS failed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_briefings_user_status_updated
    ON briefings(user_id, status, updated_at DESC);

CREATE TABLE IF NOT EXISTS agent_personas (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    is_system BOOLEAN NOT NULL,
    use_case VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    personality VARCHAR(2000) NOT NULL,
    role VARCHAR(2000) NOT NULL,
    purpose VARCHAR(2000) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    avatar_url VARCHAR(2048),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_personas_use_case
    ON agent_personas(use_case, is_system, created_at);

CREATE TABLE IF NOT EXISTS briefing_sources (
    id UUID PRIMARY KEY,
    briefing_id UUID NOT NULL REFERENCES briefings(id) ON DELETE CASCADE,
    source_id UUID NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_briefing_sources_briefing_source UNIQUE (briefing_id, source_id)
);

CREATE INDEX IF NOT EXISTS idx_briefing_sources_briefing
    ON briefing_sources(briefing_id, created_at);

CREATE TABLE IF NOT EXISTS briefing_references (
    id UUID PRIMARY KEY,
    briefing_id UUID NOT NULL REFERENCES briefings(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    url VARCHAR(2048) NOT NULL,
    title VARCHAR(500) NOT NULL,
    snippet TEXT,
    status VARCHAR(20) NOT NULL,
    promoted_to_source_id UUID REFERENCES sources(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_briefing_references_briefing_url UNIQUE (briefing_id, url)
);

CREATE INDEX IF NOT EXISTS idx_briefing_references_briefing
    ON briefing_references(briefing_id, created_at);

CREATE INDEX IF NOT EXISTS idx_briefing_references_user_status
    ON briefing_references(user_id, status);

CREATE TABLE IF NOT EXISTS briefing_plan_steps (
    id UUID PRIMARY KEY,
    briefing_id UUID NOT NULL REFERENCES briefings(id) ON DELETE CASCADE,
    persona_id UUID REFERENCES agent_personas(id) ON DELETE SET NULL,
    persona_name VARCHAR(120) NOT NULL,
    step_order INTEGER NOT NULL,
    task VARCHAR(2000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_briefing_plan_steps_briefing_order UNIQUE (briefing_id, step_order)
);

CREATE INDEX IF NOT EXISTS idx_briefing_plan_steps_briefing
    ON briefing_plan_steps(briefing_id, step_order);

CREATE TABLE IF NOT EXISTS briefing_generation_jobs (
    id UUID PRIMARY KEY,
    briefing_id UUID NOT NULL UNIQUE REFERENCES briefings(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 1,
    next_attempt_at TIMESTAMP NOT NULL,
    locked_at TIMESTAMP,
    lock_owner VARCHAR(100),
    last_error VARCHAR(4000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_briefing_generation_jobs_status_next_attempt
    ON briefing_generation_jobs(status, next_attempt_at);

INSERT INTO agent_personas (id, user_id, is_system, use_case, name, personality, role, purpose, description, avatar_url)
VALUES
    ('00000000-0000-0000-0000-000000000101', NULL, true, 'ENRICHMENT', 'The Progressive', 'Social-equity oriented analysis.', 'Progressive lens', 'Find reform-oriented perspectives.', 'Explores collective-action and equity-driven interpretations.', NULL),
    ('00000000-0000-0000-0000-000000000102', NULL, true, 'ENRICHMENT', 'The Conservative', 'Institutional-stability oriented analysis.', 'Conservative lens', 'Find tradition and stability perspectives.', 'Explores continuity, risk-control, and preservation viewpoints.', NULL),
    ('00000000-0000-0000-0000-000000000103', NULL, true, 'ENRICHMENT', 'The Libertarian', 'Autonomy-first analysis.', 'Libertarian lens', 'Find minimal-intervention perspectives.', 'Explores individual freedom and anti-centralization arguments.', NULL),
    ('00000000-0000-0000-0000-000000000104', NULL, true, 'ENRICHMENT', 'The Skeptic', 'Evidence-demanding analysis.', 'Skeptical lens', 'Challenge weak assumptions and unsupported claims.', 'Stress-tests arguments and demands high-quality evidence.', NULL),
    ('00000000-0000-0000-0000-000000000105', NULL, true, 'ENRICHMENT', 'The Scientist', 'Empirical-methodology analysis.', 'Scientific lens', 'Prioritize data and method rigor.', 'Focuses on reproducibility, study quality, and evidence hierarchy.', NULL),
    ('00000000-0000-0000-0000-000000000106', NULL, true, 'ENRICHMENT', 'The Economist', 'Incentive and tradeoff analysis.', 'Economic lens', 'Analyze costs, incentives, and externalities.', 'Surfaces systemic tradeoffs and second-order effects.', NULL),
    ('00000000-0000-0000-0000-000000000107', NULL, true, 'ENRICHMENT', 'The Technologist', 'Implementation-feasibility analysis.', 'Technology lens', 'Evaluate execution constraints and technical paths.', 'Assesses practicality, complexity, and innovation implications.', NULL)
ON CONFLICT (id) DO NOTHING;
