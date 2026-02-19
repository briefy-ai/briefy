package com.briefy.api.domain.knowledgegraph.briefing

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "briefing_plan_steps")
class BriefingPlanStep(
    @Id
    val id: UUID,

    @Column(name = "briefing_id", nullable = false)
    val briefingId: UUID,

    @Column(name = "persona_id")
    val personaId: UUID?,

    @Column(name = "persona_name", nullable = false, length = 120)
    val personaName: String,

    @Column(name = "step_order", nullable = false)
    val stepOrder: Int,

    @Column(name = "task", nullable = false, length = 2000)
    val task: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: BriefingPlanStepStatus = BriefingPlanStepStatus.PLANNED,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    fun markRunning(now: Instant = Instant.now()) {
        transitionTo(BriefingPlanStepStatus.RUNNING)
        updatedAt = now
    }

    fun markSucceeded(now: Instant = Instant.now()) {
        transitionTo(BriefingPlanStepStatus.SUCCEEDED)
        updatedAt = now
    }

    fun markFailed(now: Instant = Instant.now()) {
        transitionTo(BriefingPlanStepStatus.FAILED)
        updatedAt = now
    }

    private fun transitionTo(target: BriefingPlanStepStatus) {
        require(status.canTransitionTo(target)) {
            "Cannot transition plan step from $status to $target"
        }
        status = target
    }
}
