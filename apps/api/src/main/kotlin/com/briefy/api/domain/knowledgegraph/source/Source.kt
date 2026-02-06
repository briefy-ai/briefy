package com.briefy.api.domain.knowledgegraph.source

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "sources")
class Source(
    @Id
    val id: UUID,

    @Embedded
    val url: Url,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: SourceStatus = SourceStatus.SUBMITTED,

    @Embedded
    var content: Content? = null,

    @Embedded
    var metadata: Metadata? = null,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    fun startExtraction() {
        transitionTo(SourceStatus.EXTRACTING)
    }

    fun completeExtraction(content: Content, metadata: Metadata) {
        this.content = content
        this.metadata = metadata
        transitionTo(SourceStatus.ACTIVE)
    }

    fun failExtraction() {
        transitionTo(SourceStatus.FAILED)
    }

    fun retry() {
        transitionTo(SourceStatus.SUBMITTED)
    }

    fun archive() {
        transitionTo(SourceStatus.ARCHIVED)
    }

    private fun transitionTo(newStatus: SourceStatus) {
        require(status.canTransitionTo(newStatus)) {
            "Cannot transition from $status to $newStatus"
        }
        status = newStatus
        updatedAt = Instant.now()
    }

    companion object {
        fun create(id: UUID, rawUrl: String, userId: UUID): Source {
            return Source(
                id = id,
                url = Url.from(rawUrl),
                userId = userId
            )
        }
    }
}
