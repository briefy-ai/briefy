package com.briefy.api.application.enrichment

import com.briefy.api.domain.knowledgegraph.source.SourceEmbeddingRepository
import com.briefy.api.domain.knowledgegraph.source.SourceSimilarityMatch
import com.briefy.api.infrastructure.ai.EmbeddingAdapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class SourceSimilarityServiceTest {
    private val sourceEmbeddingRepository: SourceEmbeddingRepository = mock()
    private val embeddingAdapter: EmbeddingAdapter = mock()

    private val service = SourceSimilarityService(
        sourceEmbeddingRepository = sourceEmbeddingRepository,
        embeddingAdapter = embeddingAdapter
    )

    @Test
    fun `findSimilarSources returns mapped results`() {
        val userId = UUID.randomUUID()
        val queryEmbedding = listOf(0.3, 0.5, 0.7)
        val excludedSourceId = UUID.randomUUID()
        val expected = SourceSimilarityMatch(
            sourceId = UUID.randomUUID(),
            score = 0.91,
            title = "Related source",
            urlNormalized = "https://example.com/related",
            wordCount = 420
        )

        whenever(embeddingAdapter.embed("clean architecture notes")).thenReturn(queryEmbedding)
        whenever(
            sourceEmbeddingRepository.findSimilar(
                userId = userId,
                queryEmbedding = queryEmbedding,
                limit = 5,
                excludeSourceIds = setOf(excludedSourceId)
            )
        ).thenReturn(listOf(expected))

        val result = service.findSimilarSources(
            userId = userId,
            query = "clean architecture notes",
            limit = 5,
            excludeSourceIds = setOf(excludedSourceId)
        )

        assertEquals(1, result.size)
        assertEquals(expected.sourceId, result.first().sourceId)
        assertEquals(expected.score, result.first().score)
        assertEquals(expected.title, result.first().title)
        assertEquals(expected.urlNormalized, result.first().url)
        assertEquals(expected.wordCount, result.first().wordCount)
    }

    @Test
    fun `findSimilarSources returns empty list when query embedding fails`() {
        val userId = UUID.randomUUID()
        whenever(embeddingAdapter.embed("query")).thenThrow(RuntimeException("provider down"))

        val result = service.findSimilarSources(userId = userId, query = "query", limit = 5)

        assertTrue(result.isEmpty())
        verify(sourceEmbeddingRepository, never()).findSimilar(any(), any(), any(), any())
    }

    @Test
    fun `findSimilarSources returns empty list for blank query`() {
        val userId = UUID.randomUUID()

        val result = service.findSimilarSources(userId = userId, query = "   ", limit = 5)

        assertTrue(result.isEmpty())
        verify(embeddingAdapter, never()).embed(any())
        verify(sourceEmbeddingRepository, never()).findSimilar(any(), any(), any(), any())
    }

    @Test
    fun `findSimilarSources returns empty list when limit is non-positive`() {
        val userId = UUID.randomUUID()

        val result = service.findSimilarSources(userId = userId, query = "query", limit = 0)

        assertTrue(result.isEmpty())
        verify(embeddingAdapter, never()).embed(any())
        verify(sourceEmbeddingRepository, never()).findSimilar(any(), any(), any(), any())
    }

    @Test
    fun `findSimilarSources clamps limit to max`() {
        val userId = UUID.randomUUID()
        val queryEmbedding = listOf(0.2, 0.4)

        whenever(embeddingAdapter.embed("query")).thenReturn(queryEmbedding)
        whenever(
            sourceEmbeddingRepository.findSimilar(
                userId = eq(userId),
                queryEmbedding = eq(queryEmbedding),
                limit = eq(50),
                excludeSourceIds = eq(emptySet())
            )
        ).thenReturn(emptyList())

        service.findSimilarSources(userId = userId, query = "query", limit = 500)

        verify(sourceEmbeddingRepository).findSimilar(
            userId = eq(userId),
            queryEmbedding = eq(queryEmbedding),
            limit = eq(50),
            excludeSourceIds = eq(emptySet())
        )
    }

    @Test
    fun `findSimilarSourcesBySourceId delegates to repository`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        val excludedSourceId = UUID.randomUUID()
        val expected = SourceSimilarityMatch(
            sourceId = UUID.randomUUID(),
            score = 0.82,
            title = "Stored embedding match",
            urlNormalized = "https://example.com/stored",
            wordCount = 300
        )

        whenever(
            sourceEmbeddingRepository.findSimilarBySourceId(
                userId = userId,
                sourceId = sourceId,
                limit = 5,
                excludeSourceIds = setOf(excludedSourceId)
            )
        ).thenReturn(listOf(expected))

        val result = service.findSimilarSourcesBySourceId(
            userId = userId,
            sourceId = sourceId,
            limit = 5,
            excludeSourceIds = setOf(excludedSourceId)
        )

        assertEquals(1, result.size)
        assertEquals(expected.sourceId, result.first().sourceId)
        assertEquals(expected.urlNormalized, result.first().url)
    }

    @Test
    fun `findSimilarSourcesBySourceId returns empty list when limit is non-positive`() {
        val result = service.findSimilarSourcesBySourceId(
            userId = UUID.randomUUID(),
            sourceId = UUID.randomUUID(),
            limit = 0
        )

        assertTrue(result.isEmpty())
        verify(sourceEmbeddingRepository, never()).findSimilarBySourceId(any(), any(), any(), any())
    }
}
