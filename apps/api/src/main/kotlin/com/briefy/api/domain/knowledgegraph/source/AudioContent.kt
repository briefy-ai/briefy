package com.briefy.api.domain.knowledgegraph.source

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.time.Instant

@Embeddable
data class AudioContent(
    @Column(name = "audio_url", length = 2048)
    val audioUrl: String,

    @Column(name = "audio_duration_seconds")
    val durationSeconds: Int,

    @Column(name = "audio_format", length = 20)
    val format: String,

    @Column(name = "audio_content_hash", length = 64)
    val contentHash: String,

    @Column(name = "audio_generated_at")
    val generatedAt: Instant
)

enum class NarrationState {
    NOT_GENERATED,
    PENDING,
    SUCCEEDED,
    FAILED
}
