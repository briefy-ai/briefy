package com.briefy.api.application.source

import com.briefy.api.application.settings.ResolvedTtsProviderConfig
import com.briefy.api.application.settings.TtsSettingsService
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
import com.briefy.api.infrastructure.tts.ElevenLabsTtsException
import com.briefy.api.infrastructure.tts.InworldTtsProperties
import com.briefy.api.infrastructure.tts.MarkdownStripper
import com.briefy.api.infrastructure.tts.NarrationLanguageResolver
import com.briefy.api.infrastructure.tts.NarrationProperties
import com.briefy.api.infrastructure.tts.TtsModelCatalog
import com.briefy.api.infrastructure.tts.TtsProvider
import com.briefy.api.infrastructure.tts.TtsProviderRegistry
import com.briefy.api.infrastructure.tts.TtsProviderType
import com.briefy.api.infrastructure.tts.TtsSynthesisRequest
import com.briefy.api.infrastructure.tts.TtsVoiceResolver
import com.briefy.api.infrastructure.tts.ElevenLabsTtsProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class SourceNarrationServiceTest {
    private val sourceRepository: SourceRepository = mock()
    private val sharedAudioCacheRepository: SharedAudioCacheRepository = mock()
    private val ttsSettingsService: TtsSettingsService = mock()
    private val ttsProviderRegistry: TtsProviderRegistry = mock()
    private val ttsProvider: TtsProvider = mock()
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

    private val ttsModelCatalog = TtsModelCatalog()
    private val elevenLabsConfig = ResolvedTtsProviderConfig(
        providerType = TtsProviderType.ELEVENLABS,
        apiKey = "el-key",
        modelId = "eleven_flash_v2_5"
    )
    private val inworldConfig = ResolvedTtsProviderConfig(
        providerType = TtsProviderType.INWORLD,
        apiKey = "in-key",
        modelId = "inworld-tts-1.5-mini"
    )
    private val markdownStripper = MarkdownStripper()
    private val narrationLanguageResolver = NarrationLanguageResolver(markdownStripper)
    private val ttsVoiceResolver = TtsVoiceResolver(
        elevenLabsProperties = ElevenLabsTtsProperties(),
        inworldProperties = InworldTtsProperties()
    )

    private val service = SourceNarrationService(
        sourceRepository = sourceRepository,
        sharedAudioCacheRepository = sharedAudioCacheRepository,
        ttsSettingsService = ttsSettingsService,
        ttsProviderRegistry = ttsProviderRegistry,
        ttsModelCatalog = ttsModelCatalog,
        audioStorageService = audioStorageService,
        narrationLanguageResolver = narrationLanguageResolver,
        ttsVoiceResolver = ttsVoiceResolver,
        markdownStripper = markdownStripper,
        narrationProperties = NarrationProperties(),
        inworldProperties = InworldTtsProperties(),
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
        whenever(ttsSettingsService.resolvePreferredProvider(userId)).thenReturn(elevenLabsConfig)
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
    fun `estimateNarration returns stripped character count provider model and cost without side effects`() {
        val userId = UUID.randomUUID()
        val source = createActiveSource(userId).apply {
            content = Content.from("# Heading\n\nThis is **narration** content.")
        }
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(ttsSettingsService.resolvePreferredProvider(userId)).thenReturn(elevenLabsConfig)

        val estimate = service.estimateNarration(source.id)

        assertEquals(34, estimate.characterCount)
        assertEquals("elevenlabs", estimate.provider)
        assertEquals("eleven_flash_v2_5", estimate.modelId)
        assertEquals(BigDecimal("0.01"), estimate.estimatedCostUsd)
        verify(sourceRepository, never()).save(any())
        verify(sharedAudioCacheRepository, never()).save(any())
        verify(eventPublisher, never()).publishEvent(any())
        verify(ttsProvider, never()).synthesize(any())
    }

    @Test
    fun `processNarration completes from shared cache without calling provider`() {
        val userId = UUID.randomUUID()
        val source = createPendingSource(userId)
        val cache = SharedAudioCache(
            id = UUID.randomUUID(),
            contentHash = contentHash(source.content!!.text),
            audioUrl = "https://cached.example.com/audio.mp3",
            durationSeconds = 17,
            format = "mp3",
            characterCount = 24,
            providerType = TtsProviderType.ELEVENLABS,
            voiceId = "iiidtqDt9FBdT1vfBluA",
            modelId = "eleven_flash_v2_5",
            createdAt = Instant.parse("2026-03-15T10:00:00Z")
        )
        whenever(ttsSettingsService.resolvePreferredProvider(userId)).thenReturn(elevenLabsConfig)
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(sharedAudioCacheRepository.findByContentHashAndProviderTypeAndVoiceIdAndModelId(cache.contentHash, cache.providerType, cache.voiceId, "eleven_flash_v2_5"))
            .thenReturn(cache)
        whenever(sourceRepository.save(any())).thenAnswer { it.arguments[0] as Source }
        whenever(sharedAudioCacheRepository.save(any())).thenAnswer { it.arguments[0] as SharedAudioCache }
        whenever(audioStorageService.generatePresignedGetUrl(cache.contentHash, cache.providerType, cache.voiceId, cache.modelId))
            .thenReturn("https://fresh.example.com/audio.mp3")

        service.processNarration(source.id, userId)

        verify(ttsProvider, never()).synthesize(any())
        assertEquals(NarrationState.SUCCEEDED, source.narrationState)
        assertEquals("https://fresh.example.com/audio.mp3", source.audioContent?.audioUrl)
        assertEquals(TtsProviderType.ELEVENLABS, source.audioContent?.providerType)
    }

    @Test
    fun `processNarration generates audio uploads it and saves shared cache`() {
        val userId = UUID.randomUUID()
        val source = createPendingSource(userId).apply {
            content = Content.from("# Heading\n\nThis is **narration** content.")
        }
        val hash = contentHash(source.content!!.text)
        whenever(ttsSettingsService.resolvePreferredProvider(userId)).thenReturn(elevenLabsConfig)
        whenever(ttsProviderRegistry.get(TtsProviderType.ELEVENLABS)).thenReturn(ttsProvider)
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(sharedAudioCacheRepository.findByContentHashAndProviderTypeAndVoiceIdAndModelId(hash, TtsProviderType.ELEVENLABS, "iiidtqDt9FBdT1vfBluA", "eleven_flash_v2_5"))
            .thenReturn(null)
        whenever(ttsProvider.synthesize(any())).thenReturn(sampleMp3Bytes())
        whenever(audioStorageService.generatePresignedGetUrl(hash, TtsProviderType.ELEVENLABS, "iiidtqDt9FBdT1vfBluA", "eleven_flash_v2_5"))
            .thenReturn("https://fresh.example.com/generated.mp3")
        whenever(sourceRepository.save(any())).thenAnswer { it.arguments[0] as Source }
        whenever(sharedAudioCacheRepository.save(any())).thenAnswer { it.arguments[0] as SharedAudioCache }
        whenever(idGenerator.newId()).thenReturn(UUID.randomUUID())

        service.processNarration(source.id, userId)

        verify(ttsProvider).synthesize(
            argThat<TtsSynthesisRequest> {
                text == "Heading This is narration content." &&
                    apiKey == "el-key" &&
                    voiceId == "iiidtqDt9FBdT1vfBluA" &&
                    modelId == "eleven_flash_v2_5"
            }
        )
        val audioCaptor = argumentCaptor<ByteArray>()
        verify(audioStorageService).uploadMp3(
            eq(hash),
            eq(TtsProviderType.ELEVENLABS),
            eq("iiidtqDt9FBdT1vfBluA"),
            eq("eleven_flash_v2_5"),
            audioCaptor.capture()
        )
        assertTrue(audioCaptor.firstValue.contentEquals(sampleMp3Bytes()))
        assertEquals(NarrationState.SUCCEEDED, source.narrationState)
        assertEquals("https://fresh.example.com/generated.mp3", source.audioContent?.audioUrl)
        assertEquals(TtsProviderType.ELEVENLABS, source.audioContent?.providerType)
    }

    @Test
    fun `processNarration marks source as failed when provider throws`() {
        val userId = UUID.randomUUID()
        val source = createPendingSource(userId)
        whenever(ttsSettingsService.resolvePreferredProvider(userId)).thenReturn(elevenLabsConfig)
        whenever(ttsProviderRegistry.get(TtsProviderType.ELEVENLABS)).thenReturn(ttsProvider)
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(sharedAudioCacheRepository.findByContentHashAndProviderTypeAndVoiceIdAndModelId(any(), any(), any(), any())).thenReturn(null)
        whenever(ttsProvider.synthesize(any())).thenThrow(
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
    fun `processNarration uses spanish inworld voice for spanish content`() {
        val userId = UUID.randomUUID()
        val source = createPendingSource(userId).apply {
            content = Content.from("Hola, esta es una narracion en espanol para una fuente breve.")
            metadata = Metadata.from(
                title = "Fuente",
                author = "Autor",
                publishedDate = null,
                platform = "web",
                wordCount = content!!.wordCount,
                aiFormatted = true,
                extractionProvider = "jsoup",
                transcriptLanguage = "es"
            )
        }
        val hash = contentHash(source.content!!.text)
        whenever(ttsSettingsService.resolvePreferredProvider(userId)).thenReturn(inworldConfig)
        whenever(ttsProviderRegistry.get(TtsProviderType.INWORLD)).thenReturn(ttsProvider)
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(sharedAudioCacheRepository.findByContentHashAndProviderTypeAndVoiceIdAndModelId(hash, TtsProviderType.INWORLD, "Miguel", "inworld-tts-1.5-mini"))
            .thenReturn(null)
        whenever(ttsProvider.synthesize(any())).thenReturn(sampleMp3Bytes())
        whenever(audioStorageService.generatePresignedGetUrl(hash, TtsProviderType.INWORLD, "Miguel", "inworld-tts-1.5-mini"))
            .thenReturn("https://fresh.example.com/generated-es.mp3")
        whenever(sourceRepository.save(any())).thenAnswer { it.arguments[0] as Source }
        whenever(sharedAudioCacheRepository.save(any())).thenAnswer { it.arguments[0] as SharedAudioCache }
        whenever(idGenerator.newId()).thenReturn(UUID.randomUUID())

        service.processNarration(source.id, userId)

        verify(ttsProvider).synthesize(
            argThat<TtsSynthesisRequest> {
                voiceId == "Miguel" &&
                    modelId == "inworld-tts-1.5-mini" &&
                    apiKey == "in-key"
            }
        )
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
                    providerType = TtsProviderType.ELEVENLABS,
                    voiceId = "iiidtqDt9FBdT1vfBluA",
                    modelId = "eleven_flash_v2_5",
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
            providerType = TtsProviderType.ELEVENLABS,
            voiceId = "iiidtqDt9FBdT1vfBluA",
            modelId = "eleven_flash_v2_5",
            createdAt = Instant.parse("2026-03-15T10:00:00Z")
        )
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(audioStorageService.generatePresignedGetUrl("hash123", TtsProviderType.ELEVENLABS, "iiidtqDt9FBdT1vfBluA", "eleven_flash_v2_5"))
            .thenReturn("https://new.example.com/audio.mp3")
        whenever(sourceRepository.save(any())).thenAnswer { it.arguments[0] as Source }
        whenever(sharedAudioCacheRepository.findByContentHashAndProviderTypeAndVoiceIdAndModelId("hash123", TtsProviderType.ELEVENLABS, "iiidtqDt9FBdT1vfBluA", "eleven_flash_v2_5"))
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

    private fun contentHash(contentText: String): String {
        return NarrationContentHashing.hash(contentText)
    }

    private fun sampleMp3Bytes(): ByteArray = byteArrayOf(
        'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0, 0, 0, 0, 0,
        0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x64.toByte(),
        0x00, 0x00, 0x00, 0x00
    )
}
