package com.briefy.api.api

import com.briefy.api.application.sharing.CoverImageResult
import com.briefy.api.application.sharing.CoverImageService
import com.briefy.api.application.source.NarrationContentHashing
import com.briefy.api.domain.knowledgegraph.source.AudioContent
import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.SharedAudioCache
import com.briefy.api.domain.knowledgegraph.source.SharedAudioCacheRepository
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceType
import com.briefy.api.domain.sharing.ShareLink
import com.briefy.api.domain.sharing.ShareLinkEntityType
import com.briefy.api.domain.sharing.ShareLinkRepository
import com.briefy.api.infrastructure.imagegen.ImageStorageService
import com.briefy.api.infrastructure.security.CurrentUserProvider
import com.briefy.api.infrastructure.tts.AudioStorageService
import com.briefy.api.infrastructure.tts.TtsProviderType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class ShareLinkControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var sourceRepository: SourceRepository

    @Autowired
    lateinit var shareLinkRepository: ShareLinkRepository

    @Autowired
    lateinit var sharedAudioCacheRepository: SharedAudioCacheRepository

    @MockitoBean
    lateinit var audioStorageService: AudioStorageService

    @MockitoBean
    lateinit var imageStorageService: ImageStorageService

    @MockitoBean
    lateinit var coverImageService: CoverImageService

    @MockitoBean
    lateinit var currentUserProvider: CurrentUserProvider

    @Test
    fun `GET public share returns source narration when source audio exists`() {
        val source = saveActiveSource().apply {
            completeNarration(
                AudioContent(
                    audioUrl = "https://old.example.com/source.mp3",
                    durationSeconds = 42,
                    format = "mp3",
                    contentHash = "source-hash",
                    providerType = TtsProviderType.ELEVENLABS,
                    voiceId = "iiidtqDt9FBdT1vfBluA",
                    modelId = "eleven_flash_v2_5",
                    generatedAt = Instant.parse("2026-03-15T10:00:00Z")
                )
            )
        }
        sourceRepository.save(source)
        val token = saveShareLink(source)

        `when`(audioStorageService.generatePresignedGetUrl("source-hash", TtsProviderType.ELEVENLABS, "iiidtqDt9FBdT1vfBluA", "eleven_flash_v2_5"))
            .thenReturn("https://fresh.example.com/source.mp3")

        mockMvc.perform(get("/api/public/share/$token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.source.audio.audioUrl").value("https://fresh.example.com/source.mp3"))
            .andExpect(jsonPath("$.source.audio.durationSeconds").value(42))
            .andExpect(jsonPath("$.source.audio.format").value("mp3"))
    }

    @Test
    fun `POST share link create supports generate cover image request`() {
        val userId = UUID.randomUUID()
        val source = saveActiveSource(userId)
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(coverImageService.generateAndStore(any(), eq(userId))).thenReturn(
            CoverImageResult(
                coverKey = "images/covers/${source.id}/original.png",
                featuredKey = "images/covers/${source.id}/featured.png"
            )
        )

        mockMvc.perform(
            post("/api/v1/share-links")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"entityType":"SOURCE","entityId":"${source.id}","generateCoverImage":true}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.entityType").value("SOURCE"))
            .andExpect(jsonPath("$.entityId").value(source.id.toString()))

        val updatedSource = sourceRepository.findById(source.id).orElseThrow()
        assertEquals("images/covers/${source.id}/original.png", updatedSource.coverImageKey)
        assertEquals("images/covers/${source.id}/featured.png", updatedSource.featuredImageKey)
    }

    @Test
    fun `GET public share returns featured cover image url when available`() {
        val source = saveActiveSource().apply {
            featuredImageKey = "images/covers/$id/featured.png"
        }
        sourceRepository.save(source)
        val token = saveShareLink(source)

        whenever(imageStorageService.generatePresignedGetUrl("images/covers/${source.id}/featured.png"))
            .thenReturn("https://fresh.example.com/featured.png")

        mockMvc.perform(get("/api/public/share/$token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.source.coverImageUrl").value("https://fresh.example.com/featured.png"))
    }

    @Test
    fun `GET public share returns youtube original audio from shared cache`() {
        val source = saveActiveYoutubeSource()
        val token = saveShareLink(source)
        val contentHash = NarrationContentHashing.hash("youtube-original:dQw4w9WgXcQ")
        sharedAudioCacheRepository.save(
            SharedAudioCache(
                id = UUID.randomUUID(),
                contentHash = contentHash,
                audioUrl = "https://old.example.com/youtube.mp3",
                durationSeconds = 61,
                format = "mp3",
                characterCount = 0,
                providerType = TtsProviderType.ELEVENLABS,
                voiceId = "__youtube_original__",
                modelId = "source_audio_v1",
                createdAt = Instant.parse("2026-03-15T10:00:00Z")
            )
        )

        `when`(audioStorageService.generatePresignedGetUrl(contentHash, TtsProviderType.ELEVENLABS, "__youtube_original__", "source_audio_v1"))
            .thenReturn("https://fresh.example.com/youtube.mp3")

        mockMvc.perform(get("/api/public/share/$token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.source.audio.audioUrl").value("https://fresh.example.com/youtube.mp3"))
            .andExpect(jsonPath("$.source.audio.durationSeconds").value(61))
    }

    @Test
    fun `GET public share falls back to legacy shared audio cache hash`() {
        val source = saveActiveSource()
        val token = saveShareLink(source)
        val contentHash = legacyHash(source.content!!.text)
        sharedAudioCacheRepository.save(
            SharedAudioCache(
                id = UUID.randomUUID(),
                contentHash = contentHash,
                audioUrl = "https://old.example.com/cached.mp3",
                durationSeconds = 17,
                format = "mp3",
                characterCount = source.content!!.text.length,
                providerType = TtsProviderType.ELEVENLABS,
                voiceId = "iiidtqDt9FBdT1vfBluA",
                modelId = "eleven_flash_v2_5",
                createdAt = Instant.parse("2026-03-15T10:00:00Z")
            )
        )

        `when`(audioStorageService.generatePresignedGetUrl(contentHash, TtsProviderType.ELEVENLABS, "iiidtqDt9FBdT1vfBluA", "eleven_flash_v2_5"))
            .thenReturn("https://fresh.example.com/cached.mp3")

        mockMvc.perform(get("/api/public/share/$token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.source.audio.audioUrl").value("https://fresh.example.com/cached.mp3"))
            .andExpect(jsonPath("$.source.audio.durationSeconds").value(17))
    }

    @Test
    fun `GET public share audio refresh returns fresh source audio url`() {
        val source = saveActiveSource().apply {
            completeNarration(
                AudioContent(
                    audioUrl = "https://old.example.com/source.mp3",
                    durationSeconds = 42,
                    format = "mp3",
                    contentHash = "source-hash",
                    providerType = TtsProviderType.ELEVENLABS,
                    voiceId = "iiidtqDt9FBdT1vfBluA",
                    modelId = "eleven_flash_v2_5",
                    generatedAt = Instant.parse("2026-03-15T10:00:00Z")
                )
            )
        }
        sourceRepository.save(source)
        val token = saveShareLink(source)

        `when`(audioStorageService.generatePresignedGetUrl("source-hash", TtsProviderType.ELEVENLABS, "iiidtqDt9FBdT1vfBluA", "eleven_flash_v2_5"))
            .thenReturn("https://fresh.example.com/source.mp3")

        mockMvc.perform(get("/api/public/share/$token/audio"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.audioUrl").value("https://fresh.example.com/source.mp3"))
    }

    @Test
    fun `GET public share falls back to inworld cache using spanish voice`() {
        val source = saveActiveSource(
            contentText = "Hola, esta es una narracion compartida para una fuente en espanol.",
            transcriptLanguage = "es"
        )
        val token = saveShareLink(source)
        val contentHash = legacyHash(source.content!!.text)
        sharedAudioCacheRepository.save(
            SharedAudioCache(
                id = UUID.randomUUID(),
                contentHash = contentHash,
                audioUrl = "https://old.example.com/cached-es.mp3",
                durationSeconds = 21,
                format = "mp3",
                characterCount = source.content!!.text.length,
                providerType = TtsProviderType.INWORLD,
                voiceId = "Miguel",
                modelId = "inworld-tts-1.5-mini",
                createdAt = Instant.parse("2026-03-15T10:00:00Z")
            )
        )

        `when`(audioStorageService.generatePresignedGetUrl(contentHash, TtsProviderType.INWORLD, "Miguel", "inworld-tts-1.5-mini"))
            .thenReturn("https://fresh.example.com/cached-es.mp3")

        mockMvc.perform(get("/api/public/share/$token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.source.audio.audioUrl").value("https://fresh.example.com/cached-es.mp3"))
            .andExpect(jsonPath("$.source.audio.durationSeconds").value(21))
    }

    @Test
    fun `GET public share returns source narration for legacy source audio without stored voice id`() {
        val source = saveActiveSource().apply {
            completeNarration(
                AudioContent(
                    audioUrl = "https://old.example.com/source.mp3",
                    durationSeconds = 18,
                    format = "mp3",
                    contentHash = "legacy-source-hash",
                    providerType = TtsProviderType.ELEVENLABS,
                    voiceId = null,
                    modelId = "eleven_flash_v2_5",
                    generatedAt = Instant.parse("2026-03-15T10:00:00Z")
                )
            )
        }
        sourceRepository.save(source)
        val token = saveShareLink(source)

        `when`(audioStorageService.generatePresignedGetUrl("legacy-source-hash", TtsProviderType.ELEVENLABS, "iiidtqDt9FBdT1vfBluA", "eleven_flash_v2_5"))
            .thenReturn("https://fresh.example.com/source-legacy.mp3")

        mockMvc.perform(get("/api/public/share/$token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.source.audio.audioUrl").value("https://fresh.example.com/source-legacy.mp3"))
            .andExpect(jsonPath("$.source.audio.durationSeconds").value(18))
    }

    @Test
    fun `GET public share returns source narration for archived source with existing audio`() {
        val source = saveActiveSource().apply {
            completeNarration(
                AudioContent(
                    audioUrl = "https://old.example.com/archived.mp3",
                    durationSeconds = 24,
                    format = "mp3",
                    contentHash = "archived-hash",
                    providerType = TtsProviderType.ELEVENLABS,
                    voiceId = "iiidtqDt9FBdT1vfBluA",
                    modelId = "eleven_flash_v2_5",
                    generatedAt = Instant.parse("2026-03-15T10:00:00Z")
                )
            )
            archive()
        }
        sourceRepository.save(source)
        val token = saveShareLink(source)

        `when`(audioStorageService.generatePresignedGetUrl("archived-hash", TtsProviderType.ELEVENLABS, "iiidtqDt9FBdT1vfBluA", "eleven_flash_v2_5"))
            .thenReturn("https://fresh.example.com/archived.mp3")

        mockMvc.perform(get("/api/public/share/$token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.source.audio.audioUrl").value("https://fresh.example.com/archived.mp3"))
            .andExpect(jsonPath("$.source.audio.durationSeconds").value(24))
    }

    private fun saveShareLink(source: Source): String {
        val shareLink = ShareLink(
            token = UUID.randomUUID().toString().replace("-", ""),
            entityType = ShareLinkEntityType.SOURCE,
            entityId = source.id,
            userId = source.userId
        )
        shareLinkRepository.save(shareLink)
        return shareLink.token
    }

    private fun saveActiveSource(
        userId: UUID = UUID.randomUUID(),
        contentText: String = "Shared source narration content ${UUID.randomUUID()}",
        transcriptLanguage: String? = null
    ): Source {
        val source = Source.create(
            id = UUID.randomUUID(),
            rawUrl = "https://example.com/source/${UUID.randomUUID()}",
            userId = userId
        )
        source.startExtraction()
        val content = Content.from(contentText)
        source.completeExtraction(
            content,
            Metadata.from(
                title = "Shared Source",
                author = "Author",
                publishedDate = null,
                platform = "web",
                wordCount = content.wordCount,
                aiFormatted = true,
                extractionProvider = "jsoup",
                transcriptLanguage = transcriptLanguage
            )
        )
        return sourceRepository.save(source)
    }

    private fun saveActiveYoutubeSource(): Source {
        val userId = UUID.randomUUID()
        val source = Source.create(
            id = UUID.randomUUID(),
            rawUrl = "https://youtube.com/watch?v=dQw4w9WgXcQ",
            userId = userId,
            sourceType = SourceType.VIDEO
        )
        source.startExtraction()
        val content = Content.from("YouTube transcript")
        source.completeExtraction(
            content,
            Metadata.from(
                title = "Shared YouTube Source",
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
        return sourceRepository.save(source)
    }

    private fun legacyHash(contentText: String): String {
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(contentText.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
