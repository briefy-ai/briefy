package com.briefy.api.domain.knowledgegraph.briefing

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "run_events")
class RunEvent(
    @Id
    val id: UUID,

    @Column(name = "event_id", nullable = false, unique = true)
    val eventId: UUID,

    @Column(name = "briefing_run_id", nullable = false)
    val briefingRunId: UUID,

    @Column(name = "subagent_run_id")
    val subagentRunId: UUID? = null,

    @Column(name = "event_type", nullable = false, length = 80)
    val eventType: String,

    @Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant,

    @Column(name = "sequence_id", nullable = false, insertable = false, updatable = false)
    var sequenceId: Long = 0,

    @Column(name = "attempt")
    val attempt: Int? = null,

    @Column(name = "payload_json", columnDefinition = "TEXT")
    val payloadJson: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
