package com.briefy.api.api

import com.briefy.api.application.source.NarrationContentHashing
import com.briefy.api.domain.knowledgegraph.source.AudioContent
import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.SharedAudioCache
import com.briefy.api.domain.knowledgegraph.source.SharedAudioCacheRepository
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.sharing.ShareLink
import com.briefy.api.domain.sharing.ShareLinkEntityType
import com.briefy.api.domain.sharing.ShareLinkRepository
import com.briefy.api.infrastructure.tts.AudioStorageService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
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

    @Test
    fun `GET public share returns source narration when source audio exists`() {
        val source = saveActiveSource().apply {
            completeNarration(
                AudioContent(
                    audioUrl = "https://old.example.com/source.mp3",
                    durationSeconds = 42,
                    format = "mp3",
                    contentHash = "source-hash",
                    voiceId = "iiidtqDt9FBdT1vfBluA",
                    modelId = "eleven_flash_v2_5",
                    generatedAt = Instant.parse("2026-03-15T10:00:00Z")
                )
            )
        }
        sourceRepository.save(source)
        val token = saveShareLink(source)

        `when`(audioStorageService.generatePresignedGetUrl("source-hash", "iiidtqDt9FBdT1vfBluA", "eleven_flash_v2_5"))
            .thenReturn("https://fresh.example.com/source.mp3")

        mockMvc.perform(get("/api/public/share/$token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.source.audio.audioUrl").value("https://fresh.example.com/source.mp3"))
            .andExpect(jsonPath("$.source.audio.durationSeconds").value(42))
            .andExpect(jsonPath("$.source.audio.format").value("mp3"))
    }

    @Test
    fun `GET public share falls back to shared audio cache`() {
        val source = saveActiveSource()
        val token = saveShareLink(source)
        val contentHash = NarrationContentHashing.hash(source.content!!.text)
        sharedAudioCacheRepository.save(
            SharedAudioCache(
                id = UUID.randomUUID(),
                contentHash = contentHash,
                audioUrl = "https://old.example.com/cached.mp3",
                durationSeconds = 17,
                format = "mp3",
                characterCount = source.content!!.text.length,
                voiceId = "iiidtqDt9FBdT1vfBluA",
                modelId = "eleven_flash_v2_5",
                createdAt = Instant.parse("2026-03-15T10:00:00Z")
            )
        )

        `when`(audioStorageService.generatePresignedGetUrl(contentHash, "iiidtqDt9FBdT1vfBluA", "eleven_flash_v2_5"))
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
                    voiceId = "iiidtqDt9FBdT1vfBluA",
                    modelId = "eleven_flash_v2_5",
                    generatedAt = Instant.parse("2026-03-15T10:00:00Z")
                )
            )
        }
        sourceRepository.save(source)
        val token = saveShareLink(source)

        `when`(audioStorageService.generatePresignedGetUrl("source-hash", "iiidtqDt9FBdT1vfBluA", "eleven_flash_v2_5"))
            .thenReturn("https://fresh.example.com/source.mp3")

        mockMvc.perform(get("/api/public/share/$token/audio"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.audioUrl").value("https://fresh.example.com/source.mp3"))
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
                    voiceId = "iiidtqDt9FBdT1vfBluA",
                    modelId = "eleven_flash_v2_5",
                    generatedAt = Instant.parse("2026-03-15T10:00:00Z")
                )
            )
            archive()
        }
        sourceRepository.save(source)
        val token = saveShareLink(source)

        `when`(audioStorageService.generatePresignedGetUrl("archived-hash", "iiidtqDt9FBdT1vfBluA", "eleven_flash_v2_5"))
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

    private fun saveActiveSource(): Source {
        val userId = UUID.randomUUID()
        val contentText = "Shared source narration content ${UUID.randomUUID()}"
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
                extractionProvider = "jsoup"
            )
        )
        return sourceRepository.save(source)
    }
}
