package com.briefy.api.domain.knowledgegraph.briefing

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "synthesis_runs")
class SynthesisRun(
    @Id
    val id: UUID,

    @Column(name = "briefing_run_id", nullable = false)
    val briefingRunId: UUID,

    @Convert(converter = SynthesisRunStatusConverter::class)
    @Column(name = "status", nullable = false, length = 20)
    var status: SynthesisRunStatus,

    @Column(name = "input_persona_count", nullable = false)
    var inputPersonaCount: Int = 0,

    @Column(name = "included_persona_keys_json", columnDefinition = "TEXT")
    var includedPersonaKeysJson: String? = null,

    @Column(name = "excluded_persona_keys_json", columnDefinition = "TEXT")
    var excludedPersonaKeysJson: String? = null,

    @Column(name = "started_at")
    var startedAt: Instant? = null,

    @Column(name = "ended_at")
    var endedAt: Instant? = null,

    @Column(name = "output", columnDefinition = "TEXT")
    var output: String? = null,

    @Column(name = "last_error_code", length = 64)
    var lastErrorCode: String? = null,

    @Column(name = "last_error_message", length = 2000)
    var lastErrorMessage: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    fun transitionTo(target: SynthesisRunStatus) {
        require(status.canTransitionTo(target)) {
            "Cannot transition synthesis run from $status to $target"
        }
        status = target
    }
}
