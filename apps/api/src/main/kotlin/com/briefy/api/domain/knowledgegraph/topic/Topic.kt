package com.briefy.api.domain.knowledgegraph.topic

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "topics")
class Topic(
    @Id
    val id: UUID,

    @Column(name = "name", nullable = false, length = 200)
    var name: String,

    @Column(name = "name_normalized", nullable = false, length = 200)
    var nameNormalized: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: TopicStatus = TopicStatus.SUGGESTED,

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 20)
    val origin: TopicOrigin = TopicOrigin.SYSTEM,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    fun markSuggested() {
        transitionTo(TopicStatus.SUGGESTED)
    }

    fun activate() {
        transitionTo(TopicStatus.ACTIVE)
    }

    fun archive() {
        transitionTo(TopicStatus.ARCHIVED)
    }

    private fun transitionTo(target: TopicStatus) {
        require(status.canTransitionTo(target)) {
            "Cannot transition topic from $status to $target"
        }
        status = target
        updatedAt = Instant.now()
    }

    companion object {
        fun suggestedSystem(id: UUID, userId: UUID, name: String): Topic {
            val normalized = normalizeName(name)
            require(normalized.isNotBlank()) { "Topic name cannot be blank" }
            return Topic(
                id = id,
                userId = userId,
                name = name.trim(),
                nameNormalized = normalized,
                status = TopicStatus.SUGGESTED,
                origin = TopicOrigin.SYSTEM
            )
        }

        fun activeUser(id: UUID, userId: UUID, name: String): Topic {
            val normalized = normalizeName(name)
            require(normalized.isNotBlank()) { "Topic name cannot be blank" }
            return Topic(
                id = id,
                userId = userId,
                name = name.trim(),
                nameNormalized = normalized,
                status = TopicStatus.ACTIVE,
                origin = TopicOrigin.USER
            )
        }

        fun normalizeName(raw: String): String {
            return raw.trim()
                .lowercase()
                .replace(Regex("\\s+"), " ")
        }
    }
}
