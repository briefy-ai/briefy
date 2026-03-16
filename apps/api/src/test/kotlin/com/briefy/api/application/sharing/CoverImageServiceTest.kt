package com.briefy.api.application.sharing

import com.briefy.api.application.settings.ImageGenSettingsService
import com.briefy.api.application.settings.ResolvedImageGenConfig
import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.topiclink.SourceActiveTopicProjection
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkRepository
import com.briefy.api.infrastructure.imagegen.ImageGenerationException
import com.briefy.api.infrastructure.imagegen.ImageStorageService
import com.briefy.api.infrastructure.imagegen.OpenRouterImageClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class CoverImageServiceTest {
    private val imageGenSettingsService: ImageGenSettingsService = mock()
    private val topicLinkRepository: TopicLinkRepository = mock()
    private val openRouterImageClient: OpenRouterImageClient = mock()
    private val imageStorageService: ImageStorageService = mock()
    private val coverImageCompositor: CoverImageCompositor = mock()

    private val service = CoverImageService(
        imageGenSettingsService = imageGenSettingsService,
        topicLinkRepository = topicLinkRepository,
        openRouterImageClient = openRouterImageClient,
        imageStorageService = imageStorageService,
        coverImageCompositor = coverImageCompositor
    )

    @Test
    fun `generateAndStore returns both keys on happy path`() {
        val userId = UUID.randomUUID()
        val source = createActiveSource(userId)
        val originalBytes = "original".toByteArray()
        val featuredBytes = "featured".toByteArray()

        whenever(imageGenSettingsService.resolveConfig(userId)).thenReturn(
            ResolvedImageGenConfig(
                apiKey = "or-key",
                model = "openai/dall-e-3"
            )
        )
        whenever(topicLinkRepository.findActiveTopicsBySourceIds(userId, listOf(source.id))).thenReturn(
            listOf(
                activeTopic(source.id, "AI"),
                activeTopic(source.id, "Knowledge")
            )
        )
        whenever(openRouterImageClient.generate(eq("or-key"), eq("openai/dall-e-3"), any(), eq("1792x1024"))).thenReturn(originalBytes)
        whenever(coverImageCompositor.composite(originalBytes, "Shared Source")).thenReturn(featuredBytes)

        val result = service.generateAndStore(source, userId)

        assertEquals("images/covers/${source.id}/original.png", result?.coverKey)
        assertEquals("images/covers/${source.id}/featured.png", result?.featuredKey)
        verify(imageStorageService).uploadImage("images/covers/${source.id}/original.png", originalBytes)
        verify(imageStorageService).uploadImage("images/covers/${source.id}/featured.png", featuredBytes)

        val promptCaptor = argumentCaptor<String>()
        verify(openRouterImageClient).generate(eq("or-key"), eq("openai/dall-e-3"), promptCaptor.capture(), eq("1792x1024"))
        val prompt = promptCaptor.firstValue
        assertTrue(prompt.contains("Title: Shared Source"))
        assertTrue(prompt.contains("Topics: AI, Knowledge"))
        assertTrue(prompt.contains("Body text for the cover image prompt"))
    }

    @Test
    fun `generateAndStore returns null when provider is not configured`() {
        val userId = UUID.randomUUID()
        val source = createActiveSource(userId)
        whenever(imageGenSettingsService.resolveConfig(userId)).thenReturn(null)

        val result = service.generateAndStore(source, userId)

        assertNull(result)
        verify(openRouterImageClient, never()).generate(any(), any(), any(), any())
        verify(imageStorageService, never()).uploadImage(any(), any(), any())
    }

    @Test
    fun `generateAndStore returns null when image generation fails`() {
        val userId = UUID.randomUUID()
        val source = createActiveSource(userId)

        whenever(imageGenSettingsService.resolveConfig(userId)).thenReturn(
            ResolvedImageGenConfig(
                apiKey = "or-key",
                model = "openai/dall-e-3"
            )
        )
        whenever(topicLinkRepository.findActiveTopicsBySourceIds(userId, listOf(source.id))).thenReturn(emptyList())
        whenever(openRouterImageClient.generate(eq("or-key"), eq("openai/dall-e-3"), any(), eq("1792x1024"))).thenThrow(
            ImageGenerationException("failed")
        )

        val result = service.generateAndStore(source, userId)

        assertNull(result)
        verify(imageStorageService, never()).uploadImage(any(), any(), any())
        verify(coverImageCompositor, never()).composite(any(), any())
    }

    private fun createActiveSource(userId: UUID): Source {
        val source = Source.create(
            id = UUID.randomUUID(),
            rawUrl = "https://example.com/source",
            userId = userId
        )
        source.startExtraction()
        val content = Content.from("Body text for the cover image prompt with enough detail to build a useful scene.")
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
        return source
    }

    private fun activeTopic(sourceIdValue: UUID, name: String): SourceActiveTopicProjection {
        return object : SourceActiveTopicProjection {
            override val sourceId: UUID = sourceIdValue
            override val topicId: UUID = UUID.randomUUID()
            override val topicName: String = name
        }
    }
}
