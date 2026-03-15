package com.briefy.api.domain.knowledgegraph.source

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SharedAudioCacheRepository : JpaRepository<SharedAudioCache, UUID> {
    fun findByContentHashAndVoiceId(contentHash: String, voiceId: String): SharedAudioCache?
}
