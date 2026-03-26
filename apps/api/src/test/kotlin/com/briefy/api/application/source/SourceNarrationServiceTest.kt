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
import com.briefy.api.infrastructure.extraction.YouTubeExtractionProvider
import com.briefy.api.infrastructure.security.CurrentUserProvider
import com.briefy.api.infrastructure.tts.AudioStorageService
import com.briefy.api.infrastructure.tts.ElevenLabsTtsException
import com.briefy.api.infrastructure.tts.InworldTtsProperties
import com.briefy.api.infrastructure.tts.MarkdownStripper
import com.briefy.api.infrastructure.tts.NarrationLanguageResolver
import com.briefy.api.infrastructure.tts.NarrationProperties
import com.briefy.api.infrastructure.tts.NarrationScriptPreparer
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
    private val originalVideoAudioService: OriginalVideoAudioService = mock()
    private val youTubeExtractionProvider: YouTubeExtractionProvider = mock()
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
    private val narrationScriptPreparer = NarrationScriptPreparer(markdownStripper)
    private val narrationLanguageResolver = NarrationLanguageResolver(narrationScriptPreparer)
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
        originalVideoAudioService = originalVideoAudioService,
        youTubeExtractionProvider = youTubeExtractionProvider,
        narrationScriptPreparer = narrationScriptPreparer,
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
        assertEquals(BigDecimal("0.002"), estimate.estimatedCostUsd)
        verify(sourceRepository, never()).save(any())
        verify(sharedAudioCacheRepository, never()).save(any())
        verify(eventPublisher, never()).publishEvent(any())
        verify(ttsProvider, never()).synthesize(any())
    }

    @Test
    fun `estimateNarration uses prepared script length for structured content`() {
        val userId = UUID.randomUUID()
        val source = createActiveSource(userId).apply {
            content = Content.from(
                """
                # Inverted Indexes

                An inverted index maps terms to documents.

                across -> D0, D1, D2
                and -> D2, D3
                bat -> D3
                black -> D2

                Search for cat.
                """.trimIndent()
            )
        }
        val preparedText = "Inverted Indexes An inverted index maps terms to documents. Dense structured example skipped for audio clarity. Search for cat."
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(ttsSettingsService.resolvePreferredProvider(userId)).thenReturn(elevenLabsConfig)

        val estimate = service.estimateNarration(source.id)

        assertEquals(preparedText.length, estimate.characterCount)
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
    fun `processNarration replaces dense structured blocks before synthesis`() {
        val userId = UUID.randomUUID()
        val source = createPendingSource(userId).apply {
            content = Content.from(
                """
                # Inverted Indexes

                An inverted index maps terms to documents.

                across -> D0, D1, D2
                and -> D2, D3
                bat -> D3
                black -> D2

                Search for cat.
                """.trimIndent()
            )
        }
        val preparedText = "Inverted Indexes An inverted index maps terms to documents. Dense structured example skipped for audio clarity. Search for cat."
        val hash = contentHash(preparedText)
        whenever(ttsSettingsService.resolvePreferredProvider(userId)).thenReturn(elevenLabsConfig)
        whenever(ttsProviderRegistry.get(TtsProviderType.ELEVENLABS)).thenReturn(ttsProvider)
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(sharedAudioCacheRepository.findByContentHashAndProviderTypeAndVoiceIdAndModelId(hash, TtsProviderType.ELEVENLABS, "iiidtqDt9FBdT1vfBluA", "eleven_flash_v2_5"))
            .thenReturn(null)
        whenever(ttsProvider.synthesize(any())).thenReturn(sampleMp3Bytes())
        whenever(audioStorageService.generatePresignedGetUrl(hash, TtsProviderType.ELEVENLABS, "iiidtqDt9FBdT1vfBluA", "eleven_flash_v2_5"))
            .thenReturn("https://fresh.example.com/generated-structured.mp3")
        whenever(sourceRepository.save(any())).thenAnswer { it.arguments[0] as Source }
        whenever(sharedAudioCacheRepository.save(any())).thenAnswer { it.arguments[0] as SharedAudioCache }
        whenever(idGenerator.newId()).thenReturn(UUID.randomUUID())

        service.processNarration(source.id, userId)

        verify(ttsProvider).synthesize(
            argThat<TtsSynthesisRequest> {
                text == preparedText &&
                    apiKey == "el-key" &&
                    voiceId == "iiidtqDt9FBdT1vfBluA" &&
                    modelId == "eleven_flash_v2_5"
            }
        )
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
    fun `processNarration localizes skip annotations for spanish synthesis`() {
        val userId = UUID.randomUUID()
        val source = createPendingSource(userId).apply {
            content = Content.from(
                """
                Esta guia es para pruebas.

                ```python
                print("hola")
                ```
                """.trimIndent()
            )
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
        val preparedText = "Esta guia es para pruebas. Se omitio un ejemplo de codigo para mayor claridad del audio."
        val hash = contentHash(preparedText, "es")
        whenever(ttsSettingsService.resolvePreferredProvider(userId)).thenReturn(inworldConfig)
        whenever(ttsProviderRegistry.get(TtsProviderType.INWORLD)).thenReturn(ttsProvider)
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(sharedAudioCacheRepository.findByContentHashAndProviderTypeAndVoiceIdAndModelId(hash, TtsProviderType.INWORLD, "Miguel", "inworld-tts-1.5-mini"))
            .thenReturn(null)
        whenever(ttsProvider.synthesize(any())).thenReturn(sampleMp3Bytes())
        whenever(audioStorageService.generatePresignedGetUrl(hash, TtsProviderType.INWORLD, "Miguel", "inworld-tts-1.5-mini"))
            .thenReturn("https://fresh.example.com/generated-es-structured.mp3")
        whenever(sourceRepository.save(any())).thenAnswer { it.arguments[0] as Source }
        whenever(sharedAudioCacheRepository.save(any())).thenAnswer { it.arguments[0] as SharedAudioCache }
        whenever(idGenerator.newId()).thenReturn(UUID.randomUUID())

        service.processNarration(source.id, userId)

        verify(ttsProvider).synthesize(
            argThat<TtsSynthesisRequest> {
                text == preparedText &&
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

    @Test
    fun `refreshAudio falls back to default elevenlabs voice for legacy rows`() {
        val userId = UUID.randomUUID()
        val source = createActiveSource(userId).apply {
            completeNarration(
                AudioContent(
                    audioUrl = "https://old.example.com/audio.mp3",
                    durationSeconds = 11,
                    format = "mp3",
                    contentHash = "hash-legacy",
                    providerType = TtsProviderType.ELEVENLABS,
                    voiceId = null,
                    modelId = "eleven_flash_v2_5",
                    generatedAt = Instant.parse("2026-03-15T10:00:00Z")
                )
            )
        }
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(audioStorageService.generatePresignedGetUrl("hash-legacy", TtsProviderType.ELEVENLABS, "iiidtqDt9FBdT1vfBluA", "eleven_flash_v2_5"))
            .thenReturn("https://new.example.com/audio-legacy.mp3")
        whenever(sourceRepository.save(any())).thenAnswer { it.arguments[0] as Source }

        val refreshed = service.refreshAudio(source.id)

        assertEquals("https://new.example.com/audio-legacy.mp3", refreshed.audioUrl)
        assertEquals("https://new.example.com/audio-legacy.mp3", source.audioContent?.audioUrl)
    }

    @Test
    fun `requestNarration for youtube source does not require elevenlabs`() {
        val userId = UUID.randomUUID()
        val source = createActiveYoutubeSource(userId)
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(sourceRepository.save(any())).thenAnswer { it.arguments[0] as Source }

        val response = service.requestNarration(source.id)

        assertEquals(NarrationState.PENDING, source.narrationState)
        assertEquals("pending", response.narrationState)
    }

    @Test
    fun `processNarration for youtube source completes from original audio cache`() {
        val userId = UUID.randomUUID()
        val source = createActiveYoutubeSource(userId).apply {
            requestNarration()
        }
        val cachedAudio = AudioContent(
            audioUrl = "https://fresh.example.com/youtube.mp3",
            durationSeconds = 61,
            format = "mp3",
            contentHash = "video-hash",
            providerType = TtsProviderType.ELEVENLABS,
            voiceId = OriginalVideoAudioService.ORIGINAL_VIDEO_AUDIO_VOICE_ID,
            modelId = OriginalVideoAudioService.ORIGINAL_VIDEO_AUDIO_MODEL_ID,
            generatedAt = Instant.parse("2026-03-15T10:00:00Z")
        )
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(originalVideoAudioService.findCachedAudio("dQw4w9WgXcQ")).thenReturn(cachedAudio)
        whenever(sourceRepository.save(any())).thenAnswer { it.arguments[0] as Source }

        service.processNarration(source.id, userId)

        verify(youTubeExtractionProvider, never()).fetchOriginalAudio(any(), any())
        assertEquals(NarrationState.SUCCEEDED, source.narrationState)
        assertEquals("https://fresh.example.com/youtube.mp3", source.audioContent?.audioUrl)
    }

    @Test
    fun `processNarration for youtube source marks storage failure distinctly`() {
        val userId = UUID.randomUUID()
        val source = createActiveYoutubeSource(userId).apply {
            requestNarration()
        }
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(originalVideoAudioService.findCachedAudio("dQw4w9WgXcQ")).thenReturn(null)
        whenever(youTubeExtractionProvider.fetchOriginalAudio(source.url.normalized, 61)).thenThrow(
            SourceAudioStorageException(
                storageEndpoint = "http://localhost:9000",
                bucket = "briefy-audio",
                objectKey = "audio/elevenlabs/hash/__youtube_original__/source_audio_v1.mp3",
                cause = IllegalStateException("connection refused")
            )
        )
        whenever(sourceRepository.save(any())).thenAnswer { it.arguments[0] as Source }

        service.processNarration(source.id, userId)

        assertEquals(NarrationState.FAILED, source.narrationState)
        assertEquals("source_audio_storage_failed", source.narrationFailureReason)
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

    private fun createActiveYoutubeSource(userId: UUID): Source {
        val source = Source.create(
            id = UUID.randomUUID(),
            rawUrl = "https://youtube.com/watch?v=dQw4w9WgXcQ",
            userId = userId,
            sourceType = com.briefy.api.domain.knowledgegraph.source.SourceType.VIDEO
        )
        source.startExtraction()
        val content = Content.from("Transcript content for video")
        source.completeExtraction(
            content,
            Metadata.from(
                title = "Video",
                author = "Channel",
                publishedDate = null,
                platform = "youtube",
                wordCount = content.wordCount,
                aiFormatted = false,
                extractionProvider = "youtube",
                videoId = "dQw4w9WgXcQ",
                videoEmbedUrl = "https://www.youtube.com/embed/dQw4w9WgXcQ",
                videoDurationSeconds = 61,
                transcriptSource = "captions"
            )
        )
        return source
    }

    private fun contentHash(content: String, transcriptLanguage: String? = null): String {
        return NarrationContentHashing.hash(content, transcriptLanguage)
    }

    private fun sampleMp3Bytes(): ByteArray = byteArrayOf(
        'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0, 0, 0, 0, 0,
        0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x64.toByte(),
        0x00, 0x00, 0x00, 0x00
    )
}
