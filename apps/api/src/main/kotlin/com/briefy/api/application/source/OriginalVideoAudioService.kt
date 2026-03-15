package com.briefy.api.application.source

import com.briefy.api.domain.knowledgegraph.source.AudioContent
import com.briefy.api.domain.knowledgegraph.source.SharedAudioCache
import com.briefy.api.domain.knowledgegraph.source.SharedAudioCacheRepository
import com.briefy.api.infrastructure.id.IdGenerator
import com.briefy.api.infrastructure.tts.AudioStorageService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class OriginalVideoAudioService(
    private val sharedAudioCacheRepository: SharedAudioCacheRepository,
    private val audioStorageService: AudioStorageService,
    private val idGenerator: IdGenerator
) {
    fun findCachedAudio(videoId: String): AudioContent? {
        val cache = sharedAudioCacheRepository.findByContentHashAndVoiceIdAndModelId(
            contentHash(videoId),
            ORIGINAL_VIDEO_AUDIO_VOICE_ID,
            ORIGINAL_VIDEO_AUDIO_MODEL_ID
        ) ?: return null

        return refresh(cache)
    }

    fun store(videoId: String, durationSeconds: Int, audioBytes: ByteArray, generatedAt: Instant = Instant.now()): AudioContent {
        findCachedAudio(videoId)?.let { return it }

        val hash = contentHash(videoId)
        val objectKey = audioStorageService.objectKeyFor(hash, ORIGINAL_VIDEO_AUDIO_VOICE_ID, ORIGINAL_VIDEO_AUDIO_MODEL_ID)
        try {
            audioStorageService.uploadMp3(hash, ORIGINAL_VIDEO_AUDIO_VOICE_ID, ORIGINAL_VIDEO_AUDIO_MODEL_ID, audioBytes)
        } catch (ex: Exception) {
            throw SourceAudioStorageException(
                storageEndpoint = audioStorageService.endpoint,
                bucket = audioStorageService.bucket,
                objectKey = objectKey,
                cause = ex
            )
        }
        val audioUrl = try {
            audioStorageService.generatePresignedGetUrl(hash, ORIGINAL_VIDEO_AUDIO_VOICE_ID, ORIGINAL_VIDEO_AUDIO_MODEL_ID)
        } catch (ex: Exception) {
            throw SourceAudioPresignException(
                storageEndpoint = audioStorageService.endpoint,
                bucket = audioStorageService.bucket,
                objectKey = objectKey,
                cause = ex
            )
        }

        val cache = upsert(
            SharedAudioCache(
                id = idGenerator.newId(),
                contentHash = hash,
                audioUrl = audioUrl,
                durationSeconds = durationSeconds,
                format = AUDIO_FORMAT,
                characterCount = 0,
                voiceId = ORIGINAL_VIDEO_AUDIO_VOICE_ID,
                modelId = ORIGINAL_VIDEO_AUDIO_MODEL_ID,
                createdAt = generatedAt
            )
        )

        return cache.toAudioContent(audioUrl)
    }

    fun contentHash(videoId: String): String {
        return NarrationContentHashing.hash("youtube-original:${videoId.trim()}")
    }

    private fun refresh(cache: SharedAudioCache): AudioContent {
        val objectKey = audioStorageService.objectKeyFor(cache.contentHash, cache.voiceId, cache.modelId)
        val refreshedUrl = try {
            audioStorageService.generatePresignedGetUrl(cache.contentHash, cache.voiceId, cache.modelId)
        } catch (ex: Exception) {
            throw SourceAudioPresignException(
                storageEndpoint = audioStorageService.endpoint,
                bucket = audioStorageService.bucket,
                objectKey = objectKey,
                cause = ex
            )
        }
        if (cache.audioUrl != refreshedUrl) {
            cache.audioUrl = refreshedUrl
            sharedAudioCacheRepository.save(cache)
        }
        return cache.toAudioContent(refreshedUrl)
    }

    private fun upsert(candidate: SharedAudioCache): SharedAudioCache {
        return try {
            sharedAudioCacheRepository.findByContentHashAndVoiceIdAndModelId(
                candidate.contentHash,
                candidate.voiceId,
                candidate.modelId ?: ORIGINAL_VIDEO_AUDIO_MODEL_ID
            )?.also { existing ->
                existing.audioUrl = candidate.audioUrl
                sharedAudioCacheRepository.save(existing)
            } ?: sharedAudioCacheRepository.save(candidate)
        } catch (_: DataIntegrityViolationException) {
            sharedAudioCacheRepository.findByContentHashAndVoiceIdAndModelId(
                candidate.contentHash,
                candidate.voiceId,
                candidate.modelId ?: ORIGINAL_VIDEO_AUDIO_MODEL_ID
            )?.also { existing ->
                existing.audioUrl = candidate.audioUrl
                sharedAudioCacheRepository.save(existing)
            } ?: throw IllegalStateException("Original video audio cache conflict could not be resolved")
        }
    }

    private fun SharedAudioCache.toAudioContent(audioUrl: String): AudioContent {
        return AudioContent(
            audioUrl = audioUrl,
            durationSeconds = durationSeconds,
            format = format,
            contentHash = contentHash,
            voiceId = voiceId,
            modelId = modelId,
            generatedAt = createdAt
        )
    }

    companion object {
        const val ORIGINAL_VIDEO_AUDIO_VOICE_ID = "__youtube_original__"
        const val ORIGINAL_VIDEO_AUDIO_MODEL_ID = "source_audio_v1"
        private const val AUDIO_FORMAT = "mp3"
    }
}
