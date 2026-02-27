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

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    val sourceType: SourceType = SourceType.BLOG,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Enumerated(EnumType.STRING)
    @Column(name = "topic_extraction_state", nullable = false, length = 30)
    var topicExtractionState: TopicExtractionState = TopicExtractionState.PENDING,

    @Column(name = "topic_extraction_failure_reason", length = 255)
    var topicExtractionFailureReason: String? = null
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

    fun restore() {
        transitionTo(SourceStatus.ACTIVE)
    }

    fun markTopicExtractionPending() {
        topicExtractionState = TopicExtractionState.PENDING
        topicExtractionFailureReason = null
        updatedAt = Instant.now()
    }

    fun markTopicExtractionSucceeded() {
        topicExtractionState = TopicExtractionState.SUCCEEDED
        topicExtractionFailureReason = null
        updatedAt = Instant.now()
    }

    fun markTopicExtractionFailed(reason: String?) {
        topicExtractionState = TopicExtractionState.FAILED
        topicExtractionFailureReason = reason?.trim()?.take(255)?.ifBlank { null }
        updatedAt = Instant.now()
    }

    private fun transitionTo(newStatus: SourceStatus) {
        require(status.canTransitionTo(newStatus)) {
            "Cannot transition from $status to $newStatus"
        }
        status = newStatus
        updatedAt = Instant.now()
    }

    companion object {
        fun create(id: UUID, rawUrl: String, userId: UUID, sourceType: SourceType = SourceType.BLOG): Source {
            return Source(
                id = id,
                url = Url.from(rawUrl),
                userId = userId,
                sourceType = sourceType
            )
        }
    }
}
