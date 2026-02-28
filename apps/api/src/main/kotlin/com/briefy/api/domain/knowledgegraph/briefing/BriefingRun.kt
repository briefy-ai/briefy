package com.briefy.api.domain.knowledgegraph.briefing

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "briefing_runs")
class BriefingRun(
    @Id
    val id: UUID,

    @Column(name = "briefing_id", nullable = false)
    val briefingId: UUID,

    @Column(name = "execution_fingerprint", nullable = false, length = 128)
    val executionFingerprint: String,

    @Convert(converter = BriefingRunStatusConverter::class)
    @Column(name = "status", nullable = false, length = 20)
    var status: BriefingRunStatus,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "started_at")
    var startedAt: Instant? = null,

    @Column(name = "ended_at")
    var endedAt: Instant? = null,

    @Column(name = "deadline_at")
    var deadlineAt: Instant? = null,

    @Column(name = "total_personas", nullable = false)
    val totalPersonas: Int,

    @Column(name = "required_for_synthesis", nullable = false)
    val requiredForSynthesis: Int,

    @Column(name = "non_empty_succeeded_count", nullable = false)
    var nonEmptySucceededCount: Int = 0,

    @Column(name = "cancel_requested_at")
    var cancelRequestedAt: Instant? = null,

    @Convert(converter = BriefingRunFailureCodeConverter::class)
    @Column(name = "failure_code", length = 64)
    var failureCode: BriefingRunFailureCode? = null,

    @Column(name = "failure_message", length = 2000)
    var failureMessage: String? = null,

    @Column(name = "reused_from_run_id")
    var reusedFromRunId: UUID? = null
) {
    fun transitionTo(target: BriefingRunStatus) {
        require(status.canTransitionTo(target)) {
            "Cannot transition briefing run from $status to $target"
        }
        status = target
    }
}
