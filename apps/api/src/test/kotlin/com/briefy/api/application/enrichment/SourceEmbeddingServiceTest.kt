package com.briefy.api.application.enrichment

import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceEmbeddingRepository
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.infrastructure.ai.EmbeddingAdapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class SourceEmbeddingServiceTest {
    private val sourceRepository: SourceRepository = mock()
    private val sourceEmbeddingRepository: SourceEmbeddingRepository = mock()
    private val embeddingAdapter: EmbeddingAdapter = mock()

    private val service = SourceEmbeddingService(
        sourceRepository = sourceRepository,
        sourceEmbeddingRepository = sourceEmbeddingRepository,
        embeddingAdapter = embeddingAdapter
    )

    @Test
    fun `generateForSource upserts embedding for active source`() {
        val sourceId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val source = createActiveSource(sourceId, userId, "Embeddable source content")
        val embedding = listOf(0.1, 0.2, 0.3)

        whenever(sourceRepository.findByIdAndUserId(sourceId, userId)).thenReturn(source)
        whenever(embeddingAdapter.embed("Embeddable source content")).thenReturn(embedding)

        service.generateForSource(sourceId, userId)

        val sourceIdCaptor = argumentCaptor<UUID>()
        val userIdCaptor = argumentCaptor<UUID>()
        val embeddingCaptor = argumentCaptor<List<Double>>()
        verify(sourceEmbeddingRepository).upsert(
            sourceId = sourceIdCaptor.capture(),
            userId = userIdCaptor.capture(),
            embedding = embeddingCaptor.capture(),
            now = any()
        )
        assertEquals(source.id, sourceIdCaptor.firstValue)
        assertEquals(source.userId, userIdCaptor.firstValue)
        assertEquals(embedding, embeddingCaptor.firstValue)
    }

    @Test
    fun `generateForSource skips persistence when embedding generation fails`() {
        val sourceId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val source = createActiveSource(sourceId, userId, "Embeddable source content")

        whenever(sourceRepository.findByIdAndUserId(sourceId, userId)).thenReturn(source)
        whenever(embeddingAdapter.embed("Embeddable source content")).thenThrow(RuntimeException("boom"))

        service.generateForSource(sourceId, userId)

        verify(sourceEmbeddingRepository, never()).upsert(any(), any(), any(), any())
    }

    private fun createActiveSource(sourceId: UUID, userId: UUID, text: String): Source {
        val source = Source.create(
            id = sourceId,
            rawUrl = "https://example.com/article",
            userId = userId
        )
        source.startExtraction()
        val content = Content.from(text)
        source.completeExtraction(
            content,
            Metadata.from(
                title = "Example",
                author = "Author",
                publishedDate = Instant.parse("2025-01-01T00:00:00Z"),
                platform = "web",
                wordCount = content.wordCount,
                aiFormatted = true,
                extractionProvider = "jsoup"
            )
        )
        return source
    }
}
