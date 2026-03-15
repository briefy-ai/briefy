package com.briefy.api.domain.knowledgegraph.source

import com.briefy.api.infrastructure.tts.TtsProviderType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SharedAudioCacheRepository : JpaRepository<SharedAudioCache, UUID> {
    fun findByContentHashAndProviderTypeAndVoiceIdAndModelId(
        contentHash: String,
        providerType: TtsProviderType,
        voiceId: String,
        modelId: String
    ): SharedAudioCache?

    fun findFirstByContentHashOrderByCreatedAtDesc(contentHash: String): SharedAudioCache?
}
