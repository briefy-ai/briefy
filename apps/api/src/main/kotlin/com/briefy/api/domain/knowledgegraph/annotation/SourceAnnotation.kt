package com.briefy.api.domain.knowledgegraph.annotation

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "source_annotations")
class SourceAnnotation(
    @Id
    val id: UUID,

    @Column(name = "source_id", nullable = false)
    val sourceId: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    var body: String,

    @Column(name = "anchor_quote", nullable = false, columnDefinition = "TEXT")
    val anchorQuote: String,

    @Column(name = "anchor_prefix", nullable = false, columnDefinition = "TEXT")
    val anchorPrefix: String,

    @Column(name = "anchor_suffix", nullable = false, columnDefinition = "TEXT")
    val anchorSuffix: String,

    @Column(name = "anchor_start", nullable = false)
    val anchorStart: Int,

    @Column(name = "anchor_end", nullable = false)
    val anchorEnd: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: SourceAnnotationStatus = SourceAnnotationStatus.ACTIVE,

    @Enumerated(EnumType.STRING)
    @Column(name = "archived_cause", length = 30)
    var archivedCause: SourceAnnotationArchiveCause? = null,

    @Column(name = "archived_at")
    var archivedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    fun editBody(nextBody: String) {
        require(status == SourceAnnotationStatus.ACTIVE) {
            "Cannot edit annotation in status $status"
        }
        body = nextBody
        updatedAt = Instant.now()
    }

    fun archive(cause: SourceAnnotationArchiveCause) {
        transitionTo(SourceAnnotationStatus.ARCHIVED)
        archivedCause = cause
        archivedAt = Instant.now()
    }

    fun restore(@Suppress("UNUSED_PARAMETER") cause: SourceAnnotationArchiveCause) {
        transitionTo(SourceAnnotationStatus.ACTIVE)
        archivedCause = null
        archivedAt = null
    }

    private fun transitionTo(target: SourceAnnotationStatus) {
        require(status.canTransitionTo(target)) {
            "Cannot transition annotation from $status to $target"
        }
        status = target
        updatedAt = Instant.now()
    }

    companion object {
        fun create(
            id: UUID,
            sourceId: UUID,
            userId: UUID,
            body: String,
            anchorQuote: String,
            anchorPrefix: String,
            anchorSuffix: String,
            anchorStart: Int,
            anchorEnd: Int
        ): SourceAnnotation {
            return SourceAnnotation(
                id = id,
                sourceId = sourceId,
                userId = userId,
                body = body,
                anchorQuote = anchorQuote,
                anchorPrefix = anchorPrefix,
                anchorSuffix = anchorSuffix,
                anchorStart = anchorStart,
                anchorEnd = anchorEnd
            )
        }
    }
}
