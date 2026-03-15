package com.briefy.api.application.source

import com.briefy.api.application.settings.UserSettingsService
import com.briefy.api.domain.knowledgegraph.source.AudioContent
import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.NarrationState
import com.briefy.api.domain.knowledgegraph.source.SharedAudioCache
import com.briefy.api.domain.knowledgegraph.source.SharedAudioCacheRepository
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.event.SourceNarrationRequestedEvent
import com.briefy.api.infrastructure.id.IdGenerator
import com.briefy.api.infrastructure.security.CurrentUserProvider
import com.briefy.api.infrastructure.tts.AudioStorageService
import com.briefy.api.infrastructure.tts.ElevenLabsTtsAdapter
import com.briefy.api.infrastructure.tts.ElevenLabsTtsException
import com.briefy.api.infrastructure.tts.MarkdownStripper
import com.briefy.api.infrastructure.tts.TtsProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID

class SourceNarrationServiceTest {
    private val sourceRepository: SourceRepository = mock()
    private val sharedAudioCacheRepository: SharedAudioCacheRepository = mock()
    private val userSettingsService: UserSettingsService = mock()
    private val elevenLabsTtsAdapter: ElevenLabsTtsAdapter = mock()
    private val audioStorageService: AudioStorageService = mock()
    private val currentUserProvider: CurrentUserProvider = mock()
    private val idGenerator: IdGenerator = mock()
    private val eventPublisher: ApplicationEventPublisher = mock()

    @Suppress("UNCHECKED_CAST")
    private val transactionTemplate: TransactionTemplate = mock<TransactionTemplate>().also { template ->
        whenever(template.execute<Any?>(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as TransactionCallback<Any?>).doInTransaction(mock())
        }
    }

    private val service = SourceNarrationService(
        sourceRepository = sourceRepository,
        sharedAudioCacheRepository = sharedAudioCacheRepository,
        userSettingsService = userSettingsService,
        elevenLabsTtsAdapter = elevenLabsTtsAdapter,
        audioStorageService = audioStorageService,
        markdownStripper = MarkdownStripper(),
        ttsProperties = TtsProperties(),
        currentUserProvider = currentUserProvider,
        idGenerator = idGenerator,
        eventPublisher = eventPublisher,
        transactionTemplate = transactionTemplate
    )

    @Test
    fun `requestNarration sets pending state and publishes event`() {
        val userId = UUID.randomUUID()
        val source = createActiveSource(userId)
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(userSettingsService.getElevenlabsApiKey(userId)).thenReturn("el-key")
        whenever(sourceRepository.save(any())).thenAnswer { it.arguments[0] as Source }

        val response = service.requestNarration(source.id)

        assertEquals(NarrationState.PENDING, source.narrationState)
        assertEquals("pending", response.narrationState)
        val eventCaptor = argumentCaptor<SourceNarrationRequestedEvent>()
        verify(eventPublisher).publishEvent(eventCaptor.capture())
        assertEquals(source.id, eventCaptor.firstValue.sourceId)
        assertEquals(userId, eventCaptor.firstValue.userId)
    }

    @Test
    fun `estimateNarration returns stripped character count and model id without side effects`() {
        val userId = UUID.randomUUID()
        val source = createActiveSource(userId).apply {
            content = Content.from("# Heading\n\nThis is **narration** content.")
        }
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(userSettingsService.getElevenlabsApiKey(userId)).thenReturn("el-key")

        val estimate = service.estimateNarration(source.id)

        assertEquals(34, estimate.characterCount)
        assertEquals("eleven_flash_v2_5", estimate.modelId)
        verify(sourceRepository, never()).save(any())
        verify(sharedAudioCacheRepository, never()).save(any())
        verify(eventPublisher, never()).publishEvent(any())
        verify(elevenLabsTtsAdapter, never()).synthesize(any(), any())
        verify(audioStorageService, never()).uploadMp3(any(), any(), any())
    }

    @Test
    fun `processNarration completes from shared cache without calling ElevenLabs`() {
        val userId = UUID.randomUUID()
        val source = createPendingSource(userId)
        val cache = SharedAudioCache(
            id = UUID.randomUUID(),
            contentHash = contentHash(source.content!!.text),
            audioUrl = "https://cached.example.com/audio.mp3",
            durationSeconds = 17,
            format = "mp3",
            characterCount = 24,
            voiceId = "iiidtqDt9FBdT1vfBluA",
            createdAt = Instant.parse("2026-03-15T10:00:00Z")
        )
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(sharedAudioCacheRepository.findByContentHashAndVoiceId(cache.contentHash, cache.voiceId)).thenReturn(cache)
        whenever(sourceRepository.save(any())).thenAnswer { it.arguments[0] as Source }
        whenever(sharedAudioCacheRepository.save(any())).thenAnswer { it.arguments[0] as SharedAudioCache }
        whenever(audioStorageService.generatePresignedGetUrl(cache.contentHash, cache.voiceId))
            .thenReturn("https://fresh.example.com/audio.mp3")

        service.processNarration(source.id, userId)

        verify(elevenLabsTtsAdapter, never()).synthesize(any(), any())
        assertEquals(NarrationState.SUCCEEDED, source.narrationState)
        assertEquals("https://fresh.example.com/audio.mp3", source.audioContent?.audioUrl)
        assertEquals(17, source.audioContent?.durationSeconds)
    }

    @Test
    fun `processNarration generates audio uploads it and saves shared cache`() {
        val userId = UUID.randomUUID()
        val source = createPendingSource(userId).apply {
            content = Content.from("# Heading\n\nThis is **narration** content.")
        }
        val hash = contentHash(source.content!!.text)
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(sharedAudioCacheRepository.findByContentHashAndVoiceId(hash, "iiidtqDt9FBdT1vfBluA"))
            .thenReturn(null)
        whenever(userSettingsService.getElevenlabsApiKey(userId)).thenReturn("el-key")
        whenever(elevenLabsTtsAdapter.synthesize(any(), any())).thenReturn(sampleMp3Bytes())
        whenever(audioStorageService.generatePresignedGetUrl(hash, "iiidtqDt9FBdT1vfBluA"))
            .thenReturn("https://fresh.example.com/generated.mp3")
        whenever(sourceRepository.save(any())).thenAnswer { it.arguments[0] as Source }
        whenever(sharedAudioCacheRepository.save(any())).thenAnswer { it.arguments[0] as SharedAudioCache }
        whenever(idGenerator.newId()).thenReturn(UUID.randomUUID())

        service.processNarration(source.id, userId)

        verify(elevenLabsTtsAdapter).synthesize("Heading This is narration content.", "el-key")
        val audioCaptor = argumentCaptor<ByteArray>()
        verify(audioStorageService).uploadMp3(org.mockito.kotlin.eq(hash), org.mockito.kotlin.eq("iiidtqDt9FBdT1vfBluA"), audioCaptor.capture())
        assertTrue(audioCaptor.firstValue.contentEquals(sampleMp3Bytes()))
        assertEquals(NarrationState.SUCCEEDED, source.narrationState)
        assertEquals("https://fresh.example.com/generated.mp3", source.audioContent?.audioUrl)
        assertTrue((source.audioContent?.durationSeconds ?: 0) >= 0)
    }

    @Test
    fun `processNarration marks source as failed when TTS throws`() {
        val userId = UUID.randomUUID()
        val source = createPendingSource(userId)
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(sharedAudioCacheRepository.findByContentHashAndVoiceId(any(), any())).thenReturn(null)
        whenever(userSettingsService.getElevenlabsApiKey(userId)).thenReturn("el-key")
        whenever(elevenLabsTtsAdapter.synthesize(any(), any())).thenThrow(
            ElevenLabsTtsException(
                code = "paid_plan_required",
                userMessage = "Your ElevenLabs API key cannot use the configured voice. Free ElevenLabs plans cannot use library voices via API.",
                retryable = false
            )
        )
        whenever(sourceRepository.save(any())).thenAnswer { it.arguments[0] as Source }

        service.processNarration(source.id, userId)

        assertEquals(NarrationState.FAILED, source.narrationState)
        assertEquals("paid_plan_required", source.narrationFailureReason)
        assertNull(source.audioContent)
    }

    @Test
    fun `refreshAudio updates source and shared cache url`() {
        val userId = UUID.randomUUID()
        val source = createActiveSource(userId).apply {
            completeNarration(
                AudioContent(
                    audioUrl = "https://old.example.com/audio.mp3",
                    durationSeconds = 11,
                    format = "mp3",
                    contentHash = "hash123",
                    generatedAt = Instant.parse("2026-03-15T10:00:00Z")
                )
            )
        }
        val cache = SharedAudioCache(
            id = UUID.randomUUID(),
            contentHash = "hash123",
            audioUrl = "https://old.example.com/audio.mp3",
            durationSeconds = 11,
            format = "mp3",
            characterCount = 30,
            voiceId = "iiidtqDt9FBdT1vfBluA",
            createdAt = Instant.parse("2026-03-15T10:00:00Z")
        )
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(audioStorageService.generatePresignedGetUrl("hash123", "iiidtqDt9FBdT1vfBluA"))
            .thenReturn("https://new.example.com/audio.mp3")
        whenever(sourceRepository.save(any())).thenAnswer { it.arguments[0] as Source }
        whenever(sharedAudioCacheRepository.findByContentHashAndVoiceId("hash123", "iiidtqDt9FBdT1vfBluA"))
            .thenReturn(cache)
        whenever(sharedAudioCacheRepository.save(any())).thenAnswer { it.arguments[0] as SharedAudioCache }

        val refreshed = service.refreshAudio(source.id)

        assertEquals("https://new.example.com/audio.mp3", refreshed.audioUrl)
        assertEquals("https://new.example.com/audio.mp3", source.audioContent?.audioUrl)
        assertEquals("https://new.example.com/audio.mp3", cache.audioUrl)
    }

    private fun createActiveSource(userId: UUID): Source {
        val source = Source.create(
            id = UUID.randomUUID(),
            rawUrl = "https://example.com/${UUID.randomUUID()}",
            userId = userId
        )
        source.startExtraction()
        val content = Content.from("Narration content for testing")
        source.completeExtraction(
            content,
            Metadata.from(
                title = "Example",
                author = "Author",
                publishedDate = null,
                platform = "web",
                wordCount = content.wordCount,
                aiFormatted = true,
                extractionProvider = "jsoup"
            )
        )
        return source
    }

    private fun createPendingSource(userId: UUID): Source {
        return createActiveSource(userId).apply {
            requestNarration()
        }
    }

    private fun contentHash(content: String): String {
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun sampleMp3Bytes(): ByteArray = byteArrayOf(
        'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0, 0, 0, 0, 0,
        0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x64.toByte(),
        0, 0, 0, 0, 0, 0, 0, 0
    )
}
