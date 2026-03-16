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

    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(name = "topic_extraction_state", nullable = false, length = 30)
    var topicExtractionState: TopicExtractionState = TopicExtractionState.PENDING,

    @Column(name = "topic_extraction_failure_reason", length = 255)
    var topicExtractionFailureReason: String? = null,

    @Embedded
    var audioContent: AudioContent? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "narration_state", nullable = false, length = 20)
    var narrationState: NarrationState = NarrationState.NOT_GENERATED,

    @Column(name = "narration_failure_reason", columnDefinition = "TEXT")
    var narrationFailureReason: String? = null,

    @Column(name = "cover_image_key", length = 512)
    var coverImageKey: String? = null,

    @Column(name = "featured_image_key", length = 512)
    var featuredImageKey: String? = null
) {
    fun startExtraction() {
        transitionTo(SourceStatus.EXTRACTING)
    }

    fun completeExtraction(content: Content, metadata: Metadata) {
        this.content = content
        this.metadata = metadata
        clearGeneratedImages()
        clearNarration()
        markUnread()
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

    fun acceptManualContent(content: Content, metadata: Metadata) {
        if (status == SourceStatus.FAILED) {
            transitionTo(SourceStatus.ACTIVE)
        }
        require(status == SourceStatus.ACTIVE) {
            "Cannot provide manual content for source in status $status"
        }
        this.content = content
        this.metadata = metadata
        clearGeneratedImages()
        clearNarration()
        markUnread()
        markTopicExtractionPending()
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

    fun requestNarration() {
        require(status == SourceStatus.ACTIVE) {
            "Cannot request narration for source in status $status"
        }
        narrationState = NarrationState.PENDING
        narrationFailureReason = null
        updatedAt = Instant.now()
    }

    fun completeNarration(audioContent: AudioContent) {
        require(status == SourceStatus.ACTIVE) {
            "Cannot complete narration for source in status $status"
        }
        this.audioContent = audioContent
        narrationState = NarrationState.SUCCEEDED
        narrationFailureReason = null
        updatedAt = Instant.now()
    }

    fun failNarration(reason: String?) {
        require(status == SourceStatus.ACTIVE) {
            "Cannot fail narration for source in status $status"
        }
        audioContent = null
        narrationState = NarrationState.FAILED
        narrationFailureReason = reason?.trim()?.ifBlank { null }
        updatedAt = Instant.now()
    }

    fun clearNarration() {
        audioContent = null
        narrationState = NarrationState.NOT_GENERATED
        narrationFailureReason = null
    }

    fun hasGeneratedCoverImage(): Boolean {
        return !coverImageKey.isNullOrBlank() || !featuredImageKey.isNullOrBlank()
    }

    private fun clearGeneratedImages() {
        coverImageKey = null
        featuredImageKey = null
    }

    fun markRead(): Boolean {
        if (isRead) return false
        isRead = true
        updatedAt = Instant.now()
        return true
    }

    fun markUnread() {
        isRead = false
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
