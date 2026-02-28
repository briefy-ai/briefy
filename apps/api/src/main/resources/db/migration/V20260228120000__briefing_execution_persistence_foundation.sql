CREATE TABLE briefing_runs (
    id UUID PRIMARY KEY,
    briefing_id UUID NOT NULL REFERENCES briefings(id) ON DELETE CASCADE,
    execution_fingerprint VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    deadline_at TIMESTAMP,
    total_personas INTEGER NOT NULL,
    required_for_synthesis INTEGER NOT NULL,
    non_empty_succeeded_count INTEGER NOT NULL DEFAULT 0,
    cancel_requested_at TIMESTAMP,
    failure_code VARCHAR(64),
    failure_message VARCHAR(2000),
    reused_from_run_id UUID REFERENCES briefing_runs(id) ON DELETE SET NULL,
    CONSTRAINT chk_briefing_runs_status CHECK (
        status IN ('queued', 'running', 'cancelling', 'succeeded', 'failed', 'cancelled')
    ),
    CONSTRAINT chk_briefing_runs_total_personas CHECK (total_personas >= 1),
    CONSTRAINT chk_briefing_runs_required_for_synthesis CHECK (
        required_for_synthesis >= 1
        AND required_for_synthesis <= total_personas
    ),
    CONSTRAINT chk_briefing_runs_non_empty_succeeded_count CHECK (
        non_empty_succeeded_count >= 0
        AND non_empty_succeeded_count <= total_personas
    ),
    CONSTRAINT chk_briefing_runs_terminal_ended_at CHECK (
        (
            status IN ('succeeded', 'failed', 'cancelled')
            AND ended_at IS NOT NULL
        )
        OR (
            status IN ('queued', 'running', 'cancelling')
            AND ended_at IS NULL
        )
    )
);

CREATE INDEX idx_briefing_runs_briefing_id
    ON briefing_runs(briefing_id);

CREATE INDEX idx_briefing_runs_status
    ON briefing_runs(status);

CREATE UNIQUE INDEX uq_briefing_runs_active_per_briefing
    ON briefing_runs(briefing_id)
    WHERE status IN ('queued', 'running', 'cancelling');

CREATE TABLE subagent_runs (
    id UUID PRIMARY KEY,
    briefing_run_id UUID NOT NULL REFERENCES briefing_runs(id) ON DELETE CASCADE,
    briefing_id UUID NOT NULL REFERENCES briefings(id) ON DELETE CASCADE,
    persona_key VARCHAR(120) NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt INTEGER NOT NULL DEFAULT 1,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    deadline_at TIMESTAMP,
    curated_text TEXT,
    source_ids_used_json TEXT,
    references_used_json TEXT,
    tool_stats_json TEXT,
    last_error_code VARCHAR(64),
    last_error_retryable BOOLEAN,
    last_error_message VARCHAR(2000),
    reused BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_subagent_runs_run_persona UNIQUE (briefing_run_id, persona_key),
    CONSTRAINT chk_subagent_runs_status CHECK (
        status IN (
            'pending',
            'running',
            'retry_wait',
            'succeeded',
            'failed',
            'skipped',
            'skipped_no_output',
            'cancelled'
        )
    ),
    CONSTRAINT chk_subagent_runs_attempt_bounds CHECK (
        attempt >= 1
        AND max_attempts >= 1
        AND attempt <= max_attempts
    ),
    CONSTRAINT chk_subagent_runs_terminal_ended_at CHECK (
        (
            status IN ('succeeded', 'failed', 'skipped', 'skipped_no_output', 'cancelled')
            AND ended_at IS NOT NULL
        )
        OR (
            status IN ('pending', 'running', 'retry_wait')
            AND ended_at IS NULL
        )
    )
);

CREATE INDEX idx_subagent_runs_briefing_run_id
    ON subagent_runs(briefing_run_id);

CREATE INDEX idx_subagent_runs_briefing_run_status
    ON subagent_runs(briefing_run_id, status);

CREATE TABLE synthesis_runs (
    id UUID PRIMARY KEY,
    briefing_run_id UUID NOT NULL REFERENCES briefing_runs(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    input_persona_count INTEGER NOT NULL DEFAULT 0,
    included_persona_keys_json TEXT,
    excluded_persona_keys_json TEXT,
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    output TEXT,
    last_error_code VARCHAR(64),
    last_error_message VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_synthesis_runs_briefing_run UNIQUE (briefing_run_id),
    CONSTRAINT chk_synthesis_runs_status CHECK (
        status IN ('not_started', 'running', 'succeeded', 'failed', 'skipped', 'cancelled')
    ),
    CONSTRAINT chk_synthesis_runs_input_persona_count CHECK (input_persona_count >= 0),
    CONSTRAINT chk_synthesis_runs_terminal_ended_at CHECK (
        (
            status IN ('succeeded', 'failed', 'skipped', 'cancelled')
            AND ended_at IS NOT NULL
        )
        OR (
            status IN ('not_started', 'running')
            AND ended_at IS NULL
        )
    )
);

CREATE TABLE run_events (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    briefing_run_id UUID NOT NULL REFERENCES briefing_runs(id) ON DELETE CASCADE,
    subagent_run_id UUID REFERENCES subagent_runs(id) ON DELETE CASCADE,
    event_type VARCHAR(80) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    sequence_id BIGINT GENERATED ALWAYS AS IDENTITY,
    attempt INTEGER,
    payload_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_run_events_event_id UNIQUE (event_id)
);

CREATE INDEX idx_run_events_briefing_order
    ON run_events(briefing_run_id, occurred_at, sequence_id);

CREATE INDEX idx_run_events_subagent_order
    ON run_events(subagent_run_id, occurred_at, sequence_id);
