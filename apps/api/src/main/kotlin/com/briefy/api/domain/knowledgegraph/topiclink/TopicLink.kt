package com.briefy.api.domain.knowledgegraph.topiclink

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "topic_links")
class TopicLink(
    @Id
    val id: UUID,

    @Column(name = "topic_id", nullable = false)
    val topicId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    val targetType: TopicLinkTargetType = TopicLinkTargetType.SOURCE,

    @Column(name = "target_id", nullable = false)
    val targetId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_method", nullable = false, length = 30)
    var assignmentMethod: TopicAssignmentMethod = TopicAssignmentMethod.SYSTEM_SUGGESTED,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: TopicLinkStatus = TopicLinkStatus.SUGGESTED,

    @Column(name = "suggestion_confidence", precision = 5, scale = 4)
    var suggestionConfidence: BigDecimal? = null,

    @Column(name = "assigned_at", nullable = false)
    val assignedAt: Instant = Instant.now(),

    @Column(name = "removed_at")
    var removedAt: Instant? = null,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    fun confirm() {
        transitionTo(TopicLinkStatus.ACTIVE)
        assignmentMethod = TopicAssignmentMethod.USER_CONFIRMED
    }

    fun remove() {
        transitionTo(TopicLinkStatus.REMOVED)
        removedAt = Instant.now()
    }

    private fun transitionTo(target: TopicLinkStatus) {
        require(status.canTransitionTo(target)) {
            "Cannot transition topic link from $status to $target"
        }
        status = target
        updatedAt = Instant.now()
    }

    companion object {
        fun suggestedForSource(
            id: UUID,
            topicId: UUID,
            sourceId: UUID,
            userId: UUID,
            confidence: BigDecimal?
        ): TopicLink {
            return TopicLink(
                id = id,
                topicId = topicId,
                targetType = TopicLinkTargetType.SOURCE,
                targetId = sourceId,
                assignmentMethod = TopicAssignmentMethod.SYSTEM_SUGGESTED,
                status = TopicLinkStatus.SUGGESTED,
                suggestionConfidence = confidence,
                userId = userId
            )
        }

        fun suggestedUserForSource(
            id: UUID,
            topicId: UUID,
            sourceId: UUID,
            userId: UUID
        ): TopicLink {
            return TopicLink(
                id = id,
                topicId = topicId,
                targetType = TopicLinkTargetType.SOURCE,
                targetId = sourceId,
                assignmentMethod = TopicAssignmentMethod.USER_CREATED,
                status = TopicLinkStatus.SUGGESTED,
                suggestionConfidence = null,
                userId = userId
            )
        }

        fun activeUserForSource(
            id: UUID,
            topicId: UUID,
            sourceId: UUID,
            userId: UUID
        ): TopicLink {
            return TopicLink(
                id = id,
                topicId = topicId,
                targetType = TopicLinkTargetType.SOURCE,
                targetId = sourceId,
                assignmentMethod = TopicAssignmentMethod.USER_CREATED,
                status = TopicLinkStatus.ACTIVE,
                suggestionConfidence = null,
                userId = userId
            )
        }
    }
}
