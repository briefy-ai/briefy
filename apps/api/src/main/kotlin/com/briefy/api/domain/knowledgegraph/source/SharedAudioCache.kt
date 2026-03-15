package com.briefy.api.domain.knowledgegraph.source

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "shared_audio_cache",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_shared_audio_hash_voice_model",
            columnNames = ["content_hash", "voice_id", "model_id"]
        )
    ]
)
class SharedAudioCache(
    @Id
    val id: UUID,

    @Column(name = "content_hash", nullable = false, length = 64)
    val contentHash: String,

    @Column(name = "audio_url", nullable = false, length = 2048)
    var audioUrl: String,

    @Column(name = "duration_seconds", nullable = false)
    val durationSeconds: Int,

    @Column(name = "format", nullable = false, length = 20)
    val format: String = "mp3",

    @Column(name = "character_count", nullable = false)
    val characterCount: Int,

    @Column(name = "voice_id", nullable = false, length = 100)
    val voiceId: String,

    @Column(name = "model_id", length = 100)
    val modelId: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
