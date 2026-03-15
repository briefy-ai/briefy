package com.briefy.api.api

import com.briefy.api.application.source.SourceNarrationEventHandler
import com.briefy.api.domain.knowledgegraph.source.AudioContent
import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.NarrationState
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.infrastructure.security.CurrentUserProvider
import com.briefy.api.infrastructure.tts.AudioStorageService
import com.briefy.api.infrastructure.tts.TtsProviderType
import org.junit.jupiter.api.BeforeEach
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class SourceNarrationControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var sourceRepository: SourceRepository

    @MockitoBean
    lateinit var currentUserProvider: CurrentUserProvider

    @MockitoBean
    lateinit var sourceNarrationEventHandler: SourceNarrationEventHandler

    @MockitoBean
    lateinit var audioStorageService: AudioStorageService

    private lateinit var testUserId: UUID

    @BeforeEach
    fun setUp() {
        testUserId = UUID.randomUUID()
        `when`(currentUserProvider.requireUserId()).thenReturn(testUserId)
    }

    @Test
    fun `POST narrate sets narration state to pending`() {
        enableElevenLabs()
        val source = saveActiveSource()

        mockMvc.perform(post("/api/sources/${source.id}/narrate"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.narrationState").value("pending"))
            .andExpect(jsonPath("$.narrationFailureReason").isEmpty)
            .andExpect(jsonPath("$.audio").isEmpty)
    }

    @Test
    fun `POST narrate retry resets failed narration to pending`() {
        enableElevenLabs()
        val source = saveActiveSource().apply {
            failNarration("paid_plan_required")
        }
        sourceRepository.save(source)

        mockMvc.perform(post("/api/sources/${source.id}/narrate/retry"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.narrationState").value("pending"))
            .andExpect(jsonPath("$.narrationFailureReason").isEmpty)
    }

    @Test
    fun `GET narration estimate returns stripped character count and model id`() {
        enableElevenLabs()
        val source = saveActiveSource()

        mockMvc.perform(get("/api/sources/${source.id}/narrate/estimate"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.characterCount").value(32))
            .andExpect(jsonPath("$.provider").value("elevenlabs"))
            .andExpect(jsonPath("$.modelId").value("eleven_flash_v2_5"))
            .andExpect(jsonPath("$.estimatedCostUsd").value(0.01))
    }

    @Test
    fun `GET narration estimate returns bad request when preferred provider is not configured`() {
        val source = saveActiveSource()

        mockMvc.perform(get("/api/sources/${source.id}/narrate/estimate"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET narration estimate returns not found for another users source`() {
        enableElevenLabs()
        val source = saveActiveSource()
        val otherUserId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        `when`(currentUserProvider.requireUserId()).thenReturn(otherUserId)

        mockMvc.perform(get("/api/sources/${source.id}/narrate/estimate"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `GET source includes narration failure message and retryability`() {
        val source = saveActiveSource().apply {
            failNarration("paid_plan_required")
        }
        sourceRepository.save(source)

        mockMvc.perform(get("/api/sources/${source.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.narrationState").value("failed"))
            .andExpect(jsonPath("$.narrationFailureReason").value("paid_plan_required"))
            .andExpect(jsonPath("$.narrationFailureMessage").value("Your ElevenLabs API key cannot use the configured voice. Free ElevenLabs plans cannot use library voices via API."))
            .andExpect(jsonPath("$.narrationFailureRetryable").value(false))
    }

    @Test
    fun `GET source does not tell user to retry for non retryable request failures`() {
        val source = saveActiveSource().apply {
            failNarration("elevenlabs_request_failed")
        }
        sourceRepository.save(source)

        mockMvc.perform(get("/api/sources/${source.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.narrationFailureReason").value("elevenlabs_request_failed"))
            .andExpect(jsonPath("$.narrationFailureMessage").value("Briefy could not generate audio for this source."))
            .andExpect(jsonPath("$.narrationFailureRetryable").value(false))
    }

    @Test
    fun `GET audio refreshes presigned url`() {
        val source = saveActiveSource().apply {
            completeNarration(
                AudioContent(
                    audioUrl = "https://old.example.com/audio.mp3",
                    durationSeconds = 12,
                    format = "mp3",
                    contentHash = "abc123",
                    providerType = TtsProviderType.ELEVENLABS,
                    voiceId = "iiidtqDt9FBdT1vfBluA",
                    modelId = "eleven_flash_v2_5",
                    generatedAt = Instant.parse("2026-03-15T10:00:00Z")
                )
            )
        }
        sourceRepository.save(source)
        `when`(audioStorageService.generatePresignedGetUrl("abc123", TtsProviderType.ELEVENLABS, "iiidtqDt9FBdT1vfBluA", "eleven_flash_v2_5"))
            .thenReturn("https://new.example.com/audio.mp3")

        mockMvc.perform(get("/api/sources/${source.id}/audio"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.audioUrl").value("https://new.example.com/audio.mp3"))
            .andExpect(jsonPath("$.durationSeconds").value(12))
            .andExpect(jsonPath("$.format").value("mp3"))
            .andExpect(jsonPath("$.contentHash").value("abc123"))
    }

    private fun enableElevenLabs() {
        mockMvc.perform(
            put("/api/settings/tts/providers/elevenlabs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"enabled":true,"apiKey":"el-user-key","modelId":"eleven_flash_v2_5"}""")
        )
            .andExpect(status().isOk)
    }

    private fun saveActiveSource(): Source {
        val source = Source.create(
            id = UUID.randomUUID(),
            rawUrl = "https://example.com/source/${UUID.randomUUID()}",
            userId = testUserId
        )
        source.startExtraction()
        val content = Content.from("# Heading\n\nSource narration content")
        source.completeExtraction(
            content,
            Metadata.from(
                title = "Narration source",
                author = "Author",
                publishedDate = null,
                platform = "web",
                wordCount = content.wordCount,
                aiFormatted = true,
                extractionProvider = "jsoup"
            )
        )
        if (source.narrationState != NarrationState.NOT_GENERATED) {
            throw IllegalStateException("Expected source narration to start as NOT_GENERATED")
        }
        return sourceRepository.save(source)
    }
}
