package com.briefy.api.domain.knowledgegraph.briefing

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "subagent_runs")
class SubagentRun(
    @Id
    val id: UUID,

    @Column(name = "briefing_run_id", nullable = false)
    val briefingRunId: UUID,

    @Column(name = "briefing_id", nullable = false)
    val briefingId: UUID,

    @Column(name = "persona_key", nullable = false, length = 120)
    val personaKey: String,

    @Convert(converter = SubagentRunStatusConverter::class)
    @Column(name = "status", nullable = false, length = 30)
    var status: SubagentRunStatus,

    @Column(name = "attempt", nullable = false)
    var attempt: Int = 1,

    @Column(name = "max_attempts", nullable = false)
    var maxAttempts: Int = 3,

    @Column(name = "started_at")
    var startedAt: Instant? = null,

    @Column(name = "ended_at")
    var endedAt: Instant? = null,

    @Column(name = "deadline_at")
    var deadlineAt: Instant? = null,

    @Column(name = "curated_text", columnDefinition = "TEXT")
    var curatedText: String? = null,

    @Column(name = "source_ids_used_json", columnDefinition = "TEXT")
    var sourceIdsUsedJson: String? = null,

    @Column(name = "references_used_json", columnDefinition = "TEXT")
    var referencesUsedJson: String? = null,

    @Column(name = "tool_stats_json", columnDefinition = "TEXT")
    var toolStatsJson: String? = null,

    @Column(name = "last_error_code", length = 64)
    var lastErrorCode: String? = null,

    @Column(name = "last_error_retryable")
    var lastErrorRetryable: Boolean? = null,

    @Column(name = "last_error_message", length = 2000)
    var lastErrorMessage: String? = null,

    @Column(name = "reused", nullable = false)
    var reused: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    fun transitionTo(target: SubagentRunStatus) {
        require(status.canTransitionTo(target)) {
            "Cannot transition subagent run from $status to $target"
        }
        status = target
    }
}
