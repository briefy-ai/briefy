package com.briefy.api.application.source

import com.briefy.api.application.settings.UserSettingsService
import com.briefy.api.domain.knowledgegraph.source.AudioContent
import com.briefy.api.domain.knowledgegraph.source.NarrationState
import com.briefy.api.domain.knowledgegraph.source.SharedAudioCache
import com.briefy.api.domain.knowledgegraph.source.SharedAudioCacheRepository
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.domain.knowledgegraph.source.event.SourceNarrationRequestedEvent
import com.briefy.api.infrastructure.id.IdGenerator
import com.briefy.api.infrastructure.security.CurrentUserProvider
import com.briefy.api.infrastructure.tts.AudioStorageService
import com.briefy.api.infrastructure.tts.ElevenLabsTtsAdapter
import com.briefy.api.infrastructure.tts.ElevenLabsTtsException
import com.briefy.api.infrastructure.tts.MarkdownStripper
import com.briefy.api.infrastructure.tts.TtsProperties
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

@Service
class SourceNarrationService(
    private val sourceRepository: SourceRepository,
    private val sharedAudioCacheRepository: SharedAudioCacheRepository,
    private val userSettingsService: UserSettingsService,
    private val elevenLabsTtsAdapter: ElevenLabsTtsAdapter,
    private val audioStorageService: AudioStorageService,
    private val markdownStripper: MarkdownStripper,
    private val ttsProperties: TtsProperties,
    private val currentUserProvider: CurrentUserProvider,
    private val idGenerator: IdGenerator,
    private val eventPublisher: ApplicationEventPublisher,
    private val transactionTemplate: TransactionTemplate
) {
    private val logger = LoggerFactory.getLogger(SourceNarrationService::class.java)

    @Transactional
    fun requestNarration(id: UUID): SourceResponse {
        val userId = currentUserProvider.requireUserId()
        val source = sourceRepository.findByIdAndUserId(id, userId)
            ?: throw SourceNotFoundException(id)

        if (source.narrationState == NarrationState.SUCCEEDED || source.narrationState == NarrationState.PENDING) {
            return source.toResponse()
        }
        if (source.narrationState == NarrationState.FAILED) {
            throw InvalidSourceStateException("Narration previously failed. Use retry instead.")
        }

        validateNarrationRequest(source.status, source.content?.text)
        requireElevenLabsApiKey(userId)

        source.requestNarration()
        sourceRepository.save(source)
        eventPublisher.publishEvent(
            SourceNarrationRequestedEvent(
                sourceId = source.id,
                userId = source.userId
            )
        )

        return source.toResponse()
    }

    @Transactional
    fun retryNarration(id: UUID): SourceResponse {
        val userId = currentUserProvider.requireUserId()
        val source = sourceRepository.findByIdAndUserId(id, userId)
            ?: throw SourceNotFoundException(id)

        if (source.narrationState != NarrationState.FAILED) {
            throw InvalidSourceStateException(
                "Can only retry failed narration. Current narration state: ${source.narrationState}"
            )
        }

        validateNarrationRequest(source.status, source.content?.text)
        requireElevenLabsApiKey(userId)

        source.requestNarration()
        sourceRepository.save(source)
        eventPublisher.publishEvent(
            SourceNarrationRequestedEvent(
                sourceId = source.id,
                userId = source.userId
            )
        )

        return source.toResponse()
    }

    @Transactional
    fun refreshAudio(id: UUID): AudioContentDto {
        val userId = currentUserProvider.requireUserId()
        val source = sourceRepository.findByIdAndUserId(id, userId)
            ?: throw SourceNotFoundException(id)
        val existingAudio = source.audioContent
            ?: throw InvalidSourceStateException("Audio is not available for source $id")
        if (source.narrationState != NarrationState.SUCCEEDED) {
            throw InvalidSourceStateException(
                "Can only refresh audio for succeeded narration. Current narration state: ${source.narrationState}"
            )
        }

        val voiceId = existingAudio.voiceId ?: ttsProperties.voiceId
        val modelId = existingAudio.modelId
        val refreshedUrl = audioStorageService.generatePresignedGetUrl(existingAudio.contentHash, voiceId, modelId)
        val refreshedAudio = existingAudio.copy(audioUrl = refreshedUrl)
        source.completeNarration(refreshedAudio)
        sourceRepository.save(source)

        if (modelId != null) {
            sharedAudioCacheRepository.findByContentHashAndVoiceIdAndModelId(existingAudio.contentHash, voiceId, modelId)?.let { cache ->
                cache.audioUrl = refreshedUrl
                sharedAudioCacheRepository.save(cache)
            }
        }

        return refreshedAudio.toDto()
    }

    @Transactional(readOnly = true)
    fun estimateNarration(id: UUID): NarrationEstimateResponse {
        val userId = currentUserProvider.requireUserId()
        val source = sourceRepository.findByIdAndUserId(id, userId)
            ?: throw SourceNotFoundException(id)

        validateNarrationRequest(source.status, source.content?.text)
        requireElevenLabsApiKey(userId)

        return NarrationEstimateResponse(
            characterCount = markdownStripper.strip(source.content!!.text).length,
            modelId = ttsProperties.modelId
        )
    }

    fun processNarration(sourceId: UUID, userId: UUID) {
        val loaded = transactionTemplate.execute {
            val source = sourceRepository.findByIdAndUserId(sourceId, userId)
                ?: return@execute null
            if (source.status != SourceStatus.ACTIVE || source.narrationState != NarrationState.PENDING) {
                return@execute null
            }

            val contentText = source.content?.text?.takeIf { it.isNotBlank() }
                ?: return@execute null

            NarrationInput(
                sourceId = source.id,
                userId = source.userId,
                contentText = contentText,
                contentHash = NarrationContentHashing.hash(contentText)
            )
        } ?: return

        val plainText = markdownStripper.strip(loaded.contentText)
        if (plainText.isBlank()) {
            markNarrationFailedIfCurrent(loaded, REASON_EMPTY_CONTENT)
            return
        }
        if (plainText.length > ttsProperties.maxCharacters) {
            markNarrationFailedIfCurrent(loaded, REASON_CONTENT_TOO_LONG)
            return
        }

        val cachedAudio = transactionTemplate.execute {
            NarrationContentHashing.lookupHashes(loaded.contentText)
                .firstNotNullOfOrNull { hash ->
                    sharedAudioCacheRepository.findByContentHashAndVoiceIdAndModelId(
                        hash,
                        ttsProperties.voiceId,
                        ttsProperties.modelId
                    )
                }
        }
        if (cachedAudio != null) {
            completeFromCache(loaded, cachedAudio)
            return
        }

        val apiKey = try {
            requireElevenLabsApiKey(loaded.userId)
        } catch (ex: IllegalArgumentException) {
            markNarrationFailedIfCurrent(loaded, REASON_PROVIDER_NOT_CONFIGURED)
            logger.warn("[narration] skipped sourceId={} userId={} reason={}", loaded.sourceId, loaded.userId, REASON_PROVIDER_NOT_CONFIGURED)
            return
        }

        val audioBytes = try {
            elevenLabsTtsAdapter.synthesize(plainText, apiKey)
        } catch (ex: ElevenLabsTtsException) {
            markNarrationFailedIfCurrent(loaded, ex.code)
            logger.warn("[narration] tts_failed sourceId={} userId={} reason={}", loaded.sourceId, loaded.userId, ex.code, ex)
            return
        } catch (ex: Exception) {
            markNarrationFailedIfCurrent(loaded, REASON_TTS_FAILED)
            logger.warn("[narration] tts_failed sourceId={} userId={} reason={}", loaded.sourceId, loaded.userId, REASON_TTS_FAILED, ex)
            return
        }

        try {
            audioStorageService.uploadMp3(loaded.contentHash, ttsProperties.voiceId, ttsProperties.modelId, audioBytes)
        } catch (ex: Exception) {
            markNarrationFailedIfCurrent(loaded, REASON_STORAGE_FAILED)
            logger.warn(
                "[narration] storage_failed sourceId={} userId={} reason={}",
                loaded.sourceId,
                loaded.userId,
                REASON_STORAGE_FAILED,
                ex
            )
            return
        }

        val generatedAt = Instant.now()
        val audioUrl = try {
            audioStorageService.generatePresignedGetUrl(loaded.contentHash, ttsProperties.voiceId, ttsProperties.modelId)
        } catch (ex: Exception) {
            markNarrationFailedIfCurrent(loaded, REASON_AUDIO_REFRESH_FAILED)
            logger.warn(
                "[narration] presign_failed sourceId={} userId={} reason={}",
                loaded.sourceId,
                loaded.userId,
                REASON_AUDIO_REFRESH_FAILED,
                ex
            )
            return
        }

        val durationSeconds = Mp3DurationCalculator.calculate(audioBytes)
        val sharedAudio = upsertSharedAudioCache(
            SharedAudioCache(
                id = idGenerator.newId(),
                contentHash = loaded.contentHash,
                audioUrl = audioUrl,
                durationSeconds = durationSeconds,
                format = AUDIO_FORMAT,
                characterCount = plainText.length,
                voiceId = ttsProperties.voiceId,
                modelId = ttsProperties.modelId,
                createdAt = generatedAt
            )
        )

        completeNarrationIfCurrent(
            loaded = loaded,
            audioContent = AudioContent(
                audioUrl = sharedAudio.audioUrl,
                durationSeconds = sharedAudio.durationSeconds,
                format = sharedAudio.format,
                contentHash = sharedAudio.contentHash,
                voiceId = sharedAudio.voiceId,
                modelId = sharedAudio.modelId,
                generatedAt = sharedAudio.createdAt
            )
        )

        logger.info(
            "[narration] completed sourceId={} userId={} contentHash={} cached=false durationSeconds={}",
            loaded.sourceId,
            loaded.userId,
            loaded.contentHash,
            durationSeconds
        )
    }

    private fun completeFromCache(loaded: NarrationInput, cachedAudio: SharedAudioCache) {
        val refreshedUrl = try {
            audioStorageService.generatePresignedGetUrl(cachedAudio.contentHash, cachedAudio.voiceId, cachedAudio.modelId)
        } catch (ex: Exception) {
            markNarrationFailedIfCurrent(loaded, REASON_AUDIO_REFRESH_FAILED)
            logger.warn(
                "[narration] cache_refresh_failed sourceId={} userId={} reason={}",
                loaded.sourceId,
                loaded.userId,
                REASON_AUDIO_REFRESH_FAILED,
                ex
            )
            return
        }

        transactionTemplate.execute<Unit?> {
            val modelId = cachedAudio.modelId ?: return@execute null
            sharedAudioCacheRepository.findByContentHashAndVoiceIdAndModelId(
                cachedAudio.contentHash,
                cachedAudio.voiceId,
                modelId
            )?.let { cache ->
                cache.audioUrl = refreshedUrl
                sharedAudioCacheRepository.save(cache)
            }
            null
        }

        completeNarrationIfCurrent(
            loaded = loaded,
            audioContent = AudioContent(
                audioUrl = refreshedUrl,
                durationSeconds = cachedAudio.durationSeconds,
                format = cachedAudio.format,
                contentHash = cachedAudio.contentHash,
                voiceId = cachedAudio.voiceId,
                modelId = cachedAudio.modelId,
                generatedAt = cachedAudio.createdAt
            )
        )

        logger.info(
            "[narration] completed sourceId={} userId={} contentHash={} cached=true durationSeconds={}",
            loaded.sourceId,
            loaded.userId,
            loaded.contentHash,
            cachedAudio.durationSeconds
        )
    }

    private fun completeNarrationIfCurrent(loaded: NarrationInput, audioContent: AudioContent) {
        transactionTemplate.execute<Unit?> {
            val source = sourceRepository.findByIdAndUserId(loaded.sourceId, loaded.userId)
                ?: return@execute null
            if (source.status != SourceStatus.ACTIVE || source.narrationState != NarrationState.PENDING) {
                return@execute null
            }
            if (NarrationContentHashing.hash(source.content?.text.orEmpty()) != loaded.contentHash) {
                return@execute null
            }

            source.completeNarration(audioContent)
            sourceRepository.save(source)
            null
        }
    }

    private fun markNarrationFailedIfCurrent(loaded: NarrationInput, reason: String) {
        transactionTemplate.execute<Unit?> {
            val source = sourceRepository.findByIdAndUserId(loaded.sourceId, loaded.userId)
                ?: return@execute null
            if (source.status != SourceStatus.ACTIVE || source.narrationState != NarrationState.PENDING) {
                return@execute null
            }
            if (NarrationContentHashing.hash(source.content?.text.orEmpty()) != loaded.contentHash) {
                return@execute null
            }

            source.failNarration(reason)
            sourceRepository.save(source)
            null
        }
    }

    private fun upsertSharedAudioCache(candidate: SharedAudioCache): SharedAudioCache {
        return try {
            transactionTemplate.execute {
                sharedAudioCacheRepository.findByContentHashAndVoiceIdAndModelId(
                    candidate.contentHash,
                    candidate.voiceId,
                    candidate.modelId ?: ttsProperties.modelId
                )
                    ?.also { existing ->
                        existing.audioUrl = candidate.audioUrl
                        sharedAudioCacheRepository.save(existing)
                    }
                    ?: sharedAudioCacheRepository.save(candidate)
            } ?: candidate
        } catch (_: DataIntegrityViolationException) {
            transactionTemplate.execute {
                sharedAudioCacheRepository.findByContentHashAndVoiceIdAndModelId(
                    candidate.contentHash,
                    candidate.voiceId,
                    candidate.modelId ?: ttsProperties.modelId
                )?.also { existing ->
                    existing.audioUrl = candidate.audioUrl
                    sharedAudioCacheRepository.save(existing)
                }
            } ?: throw IllegalStateException("Shared audio cache conflict could not be resolved")
        }
    }

    private fun validateNarrationRequest(status: SourceStatus, contentText: String?) {
        if (status != SourceStatus.ACTIVE) {
            throw InvalidSourceStateException("Can only narrate active sources. Current status: $status")
        }
        if (contentText.isNullOrBlank()) {
            throw InvalidSourceStateException("Cannot narrate a source without extracted content")
        }
    }

    private fun requireElevenLabsApiKey(userId: UUID): String {
        return userSettingsService.getElevenlabsApiKey(userId)
            ?: throw IllegalArgumentException("ElevenLabs is not enabled or configured in Settings")
    }

    private data class NarrationInput(
        val sourceId: UUID,
        val userId: UUID,
        val contentText: String,
        val contentHash: String
    )

    companion object {
        private const val AUDIO_FORMAT = "mp3"
        private const val REASON_EMPTY_CONTENT = "empty_plaintext_content"
        private const val REASON_CONTENT_TOO_LONG = "content_too_long"
        private const val REASON_PROVIDER_NOT_CONFIGURED = "elevenlabs_not_configured"
        private const val REASON_TTS_FAILED = "tts_generation_failed"
        private const val REASON_STORAGE_FAILED = "audio_storage_failed"
        private const val REASON_AUDIO_REFRESH_FAILED = "audio_url_refresh_failed"
    }
}
