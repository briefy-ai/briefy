package com.briefy.api.application.sharing

import com.briefy.api.application.settings.ImageGenSettingsService
import com.briefy.api.application.settings.ResolvedImageGenConfig
import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.topiclink.SourceActiveTopicProjection
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkRepository
import com.briefy.api.infrastructure.ai.AiAdapter
import com.briefy.api.infrastructure.imagegen.ImageGenerationException
import com.briefy.api.infrastructure.imagegen.ImageStorageService
import com.briefy.api.infrastructure.imagegen.OpenRouterImageClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
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
    private val aiAdapter: AiAdapter = mock()
    private val openRouterImageClient: OpenRouterImageClient = mock()
    private val imageStorageService: ImageStorageService = mock()
    private val coverImageCompositor: CoverImageCompositor = mock()

    private val service = createService()

    @Test
    fun `generateAndStore returns both keys on happy path`() {
        val userId = UUID.randomUUID()
        val source = createActiveSource(userId)
        val originalBytes = "original".toByteArray()
        val featuredBytes = "featured".toByteArray()

        whenever(imageGenSettingsService.resolveConfig(userId)).thenReturn(
            ResolvedImageGenConfig(
                apiKey = "or-key",
                model = "google/gemini-3.1-flash-image-preview"
            )
        )
        whenever(topicLinkRepository.findActiveTopicsBySourceIds(userId, listOf(source.id))).thenReturn(
            listOf(
                activeTopic(source.id, "AI"),
                activeTopic(source.id, "Knowledge")
            )
        )
        whenever(aiAdapter.complete(eq("google_genai"), eq("gemini-2.5-flash"), any(), any(), eq("cover_image_prompt_crafting")))
            .thenReturn("A calm cinematic newsroom at dawn, warm window light over layered research notes, subtle depth, editorial mood, uncluttered center, rich texture, human scale, no text.")
        whenever(openRouterImageClient.generate(eq("or-key"), eq("google/gemini-3.1-flash-image-preview"), any(), eq("1792x1024"))).thenReturn(originalBytes)
        whenever(coverImageCompositor.composite(originalBytes, "Shared Source")).thenReturn(featuredBytes)

        val result = service.generateAndStore(source, userId)

        assertEquals("images/covers/${source.id}/original.png", result?.coverKey)
        assertEquals("images/covers/${source.id}/featured.jpg", result?.featuredKey)
        verify(imageStorageService).uploadImage("images/covers/${source.id}/original.png", originalBytes)
        verify(imageStorageService).uploadImage("images/covers/${source.id}/featured.jpg", featuredBytes)

        val promptCaptor = argumentCaptor<String>()
        verify(openRouterImageClient).generate(eq("or-key"), eq("google/gemini-3.1-flash-image-preview"), promptCaptor.capture(), eq("1792x1024"))
        assertEquals(
            "A calm cinematic newsroom at dawn, warm window light over layered research notes, subtle depth, editorial mood, uncluttered center, rich texture, human scale, no text.",
            promptCaptor.firstValue
        )
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
    fun `generateAndStore falls back to raw prompt when prompt crafting fails`() {
        val userId = UUID.randomUUID()
        val source = createActiveSource(userId)
        val originalBytes = "original".toByteArray()
        val featuredBytes = "featured".toByteArray()

        whenever(imageGenSettingsService.resolveConfig(userId)).thenReturn(
            ResolvedImageGenConfig(
                apiKey = "or-key",
                model = "google/gemini-3.1-flash-image-preview"
            )
        )
        whenever(topicLinkRepository.findActiveTopicsBySourceIds(userId, listOf(source.id))).thenReturn(
            listOf(activeTopic(source.id, "AI"))
        )
        whenever(aiAdapter.complete(eq("google_genai"), eq("gemini-2.5-flash"), any(), any(), eq("cover_image_prompt_crafting")))
            .thenThrow(RuntimeException("boom"))
        whenever(openRouterImageClient.generate(eq("or-key"), eq("google/gemini-3.1-flash-image-preview"), any(), eq("1792x1024"))).thenReturn(originalBytes)
        whenever(coverImageCompositor.composite(originalBytes, "Shared Source")).thenReturn(featuredBytes)

        val result = service.generateAndStore(source, userId)

        assertEquals("images/covers/${source.id}/original.png", result?.coverKey)
        val promptCaptor = argumentCaptor<String>()
        verify(openRouterImageClient).generate(eq("or-key"), eq("google/gemini-3.1-flash-image-preview"), promptCaptor.capture(), eq("1792x1024"))
        assertTrue(promptCaptor.firstValue.contains("Title: Shared Source"))
        assertTrue(promptCaptor.firstValue.contains("Topics: AI"))
        assertTrue(promptCaptor.firstValue.contains("Body text for the cover image prompt"))
    }

    @Test
    fun `generateAndStore skips prompt crafting when disabled`() {
        val disabledService = createService(promptCraftingEnabled = false)
        val userId = UUID.randomUUID()
        val source = createActiveSource(userId)
        val originalBytes = "original".toByteArray()
        val featuredBytes = "featured".toByteArray()

        whenever(imageGenSettingsService.resolveConfig(userId)).thenReturn(
            ResolvedImageGenConfig(
                apiKey = "or-key",
                model = "google/gemini-3.1-flash-image-preview"
            )
        )
        whenever(topicLinkRepository.findActiveTopicsBySourceIds(userId, listOf(source.id))).thenReturn(
            listOf(activeTopic(source.id, "AI"))
        )
        whenever(openRouterImageClient.generate(eq("or-key"), eq("google/gemini-3.1-flash-image-preview"), any(), eq("1792x1024"))).thenReturn(originalBytes)
        whenever(coverImageCompositor.composite(originalBytes, "Shared Source")).thenReturn(featuredBytes)

        val result = disabledService.generateAndStore(source, userId)

        assertEquals("images/covers/${source.id}/original.png", result?.coverKey)
        verify(aiAdapter, never()).complete(any(), any(), any(), anyOrNull(), anyOrNull())
        val promptCaptor = argumentCaptor<String>()
        verify(openRouterImageClient).generate(eq("or-key"), eq("google/gemini-3.1-flash-image-preview"), promptCaptor.capture(), eq("1792x1024"))
        assertTrue(promptCaptor.firstValue.contains("Title: Shared Source"))
        assertTrue(promptCaptor.firstValue.contains("Topics: AI"))
        assertTrue(promptCaptor.firstValue.contains("Body text for the cover image prompt"))
    }

    @Test
    fun `generateAndStore returns null when image generation fails`() {
        val userId = UUID.randomUUID()
        val source = createActiveSource(userId)

        whenever(imageGenSettingsService.resolveConfig(userId)).thenReturn(
            ResolvedImageGenConfig(
                apiKey = "or-key",
                model = "google/gemini-3.1-flash-image-preview"
            )
        )
        whenever(topicLinkRepository.findActiveTopicsBySourceIds(userId, listOf(source.id))).thenReturn(emptyList())
        whenever(aiAdapter.complete(eq("google_genai"), eq("gemini-2.5-flash"), any(), any(), eq("cover_image_prompt_crafting")))
            .thenReturn("A calm cinematic editorial illustration with gentle depth and an uncluttered center.")
        whenever(openRouterImageClient.generate(eq("or-key"), eq("google/gemini-3.1-flash-image-preview"), any(), eq("1792x1024"))).thenThrow(
            ImageGenerationException("failed")
        )

        val result = service.generateAndStore(source, userId)

        assertNull(result)
        verify(imageStorageService, never()).uploadImage(any(), any(), any())
        verify(coverImageCompositor, never()).composite(any(), any())
    }

    private fun createService(promptCraftingEnabled: Boolean = true): CoverImageService {
        return CoverImageService(
            imageGenSettingsService = imageGenSettingsService,
            topicLinkRepository = topicLinkRepository,
            aiAdapter = aiAdapter,
            openRouterImageClient = openRouterImageClient,
            imageStorageService = imageStorageService,
            coverImageCompositor = coverImageCompositor,
            promptCraftingEnabled = promptCraftingEnabled,
            promptCraftingProvider = "google_genai",
            promptCraftingModel = "gemini-2.5-flash"
        )
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
