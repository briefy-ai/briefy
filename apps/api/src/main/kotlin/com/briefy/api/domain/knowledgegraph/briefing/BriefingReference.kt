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
@Table(name = "briefing_references")
class BriefingReference(
    @Id
    val id: UUID,

    @Column(name = "briefing_id", nullable = false)
    val briefingId: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "url", nullable = false, length = 2048)
    val url: String,

    @Column(name = "title", nullable = false, length = 500)
    val title: String,

    @Column(name = "snippet")
    val snippet: String?,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: BriefingReferenceStatus = BriefingReferenceStatus.ACTIVE,

    @Column(name = "promoted_to_source_id")
    var promotedToSourceId: UUID? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    fun markPromoted(sourceId: UUID, now: Instant = Instant.now()) {
        require(status.canTransitionTo(BriefingReferenceStatus.PROMOTED)) {
            "Cannot transition reference from $status to ${BriefingReferenceStatus.PROMOTED}"
        }
        status = BriefingReferenceStatus.PROMOTED
        promotedToSourceId = sourceId
        updatedAt = now
    }
}
