package com.briefy.api.domain.knowledgegraph.source

import com.briefy.api.infrastructure.tts.TtsProviderType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
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

    @Enumerated(EnumType.STRING)
    @Column(name = "audio_provider_type", length = 30)
    val providerType: TtsProviderType? = null,

    @Column(name = "audio_voice_id", length = 100)
    val voiceId: String? = null,

    @Column(name = "audio_model_id", length = 100)
    val modelId: String? = null,

    @Column(name = "audio_generated_at")
    val generatedAt: Instant
)

enum class NarrationState {
    NOT_GENERATED,
    PENDING,
    SUCCEEDED,
    FAILED
}
