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
@Table(name = "briefings")
class Briefing(
    @Id
    val id: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "enrichment_intent", nullable = false, length = 40)
    val enrichmentIntent: BriefingEnrichmentIntent,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    var status: BriefingStatus = BriefingStatus.PLAN_PENDING_APPROVAL,

    @Column(name = "content_markdown", columnDefinition = "TEXT")
    var contentMarkdown: String? = null,

    @Column(name = "citations_json", columnDefinition = "TEXT")
    var citationsJson: String? = null,

    @Column(name = "conflict_highlights_json", columnDefinition = "TEXT")
    var conflictHighlightsJson: String? = null,

    @Column(name = "error_json", columnDefinition = "TEXT")
    var errorJson: String? = null,

    @Column(name = "planned_at")
    var plannedAt: Instant? = null,

    @Column(name = "approved_at")
    var approvedAt: Instant? = null,

    @Column(name = "generation_started_at")
    var generationStartedAt: Instant? = null,

    @Column(name = "generation_completed_at")
    var generationCompletedAt: Instant? = null,

    @Column(name = "failed_at")
    var failedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    fun markPlanned(now: Instant = Instant.now()) {
        require(status == BriefingStatus.PLAN_PENDING_APPROVAL || status == BriefingStatus.FAILED) {
            "Cannot mark plan in status $status"
        }
        status = BriefingStatus.PLAN_PENDING_APPROVAL
        plannedAt = now
        updatedAt = now
    }

    fun approve(now: Instant = Instant.now()) {
        transitionTo(BriefingStatus.APPROVED)
        approvedAt = now
        updatedAt = now
    }

    fun startGeneration(now: Instant = Instant.now()) {
        transitionTo(BriefingStatus.GENERATING)
        generationStartedAt = now
        failedAt = null
        updatedAt = now
    }

    fun completeGeneration(
        markdown: String,
        citationsJson: String,
        conflictHighlightsJson: String?,
        now: Instant = Instant.now()
    ) {
        transitionTo(BriefingStatus.READY)
        contentMarkdown = markdown
        this.citationsJson = citationsJson
        this.conflictHighlightsJson = conflictHighlightsJson
        errorJson = null
        generationCompletedAt = now
        failedAt = null
        updatedAt = now
    }

    fun failGeneration(errorJson: String, now: Instant = Instant.now()) {
        transitionTo(BriefingStatus.FAILED)
        this.errorJson = errorJson
        failedAt = now
        updatedAt = now
    }

    fun resetForRetry(now: Instant = Instant.now()) {
        transitionTo(BriefingStatus.PLAN_PENDING_APPROVAL)
        contentMarkdown = null
        citationsJson = null
        conflictHighlightsJson = null
        errorJson = null
        approvedAt = null
        generationStartedAt = null
        generationCompletedAt = null
        failedAt = null
        plannedAt = now
        updatedAt = now
    }

    private fun transitionTo(target: BriefingStatus) {
        require(status.canTransitionTo(target)) {
            "Cannot transition briefing from $status to $target"
        }
        status = target
    }

    companion object {
        fun create(
            id: UUID,
            userId: UUID,
            enrichmentIntent: BriefingEnrichmentIntent,
            now: Instant = Instant.now()
        ): Briefing {
            return Briefing(
                id = id,
                userId = userId,
                enrichmentIntent = enrichmentIntent,
                status = BriefingStatus.PLAN_PENDING_APPROVAL,
                createdAt = now,
                updatedAt = now,
                plannedAt = now
            )
        }
    }
}
