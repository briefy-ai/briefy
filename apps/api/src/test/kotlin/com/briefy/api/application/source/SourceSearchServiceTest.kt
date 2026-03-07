package com.briefy.api.application.source

import com.briefy.api.application.enrichment.SimilarSourceResult
import com.briefy.api.application.enrichment.SourceSimilarityService
import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class SourceSearchServiceTest {
    private val sourceSimilarityService: SourceSimilarityService = mock()
    private val sourceRepository: SourceRepository = mock()
    private val service = SourceSearchService(
        sourceSimilarityService = sourceSimilarityService,
        sourceRepository = sourceRepository
    )

    @Test
    fun `returns similarity matches with source snippets`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        val request = SourceSearchRequest(
            userId = userId,
            query = "diagnostics",
            mode = SourceSearchMode.SIMILARITY,
            limit = 5
        )

        whenever(sourceSimilarityService.findSimilarSources(userId, "diagnostics", 5, null)).thenReturn(
            listOf(
                SimilarSourceResult(
                    sourceId = sourceId,
                    score = 0.88,
                    title = "Ignored title",
                    url = "https://example.com/ignored",
                    wordCount = 320
                )
            )
        )
        whenever(sourceRepository.findAllByUserIdAndIdIn(eq(userId), eq(listOf(sourceId)))).thenReturn(
            listOf(
                sourceWithContent(
                    sourceId = sourceId,
                    userId = userId,
                    title = "Diagnostics Update",
                    url = "https://example.com/diagnostics",
                    text = "Internal source content"
                )
            )
        )

        val results = service.search(request)

        assertEquals(1, results.size)
        assertEquals(sourceId, results.first().sourceId)
        assertEquals("Diagnostics Update", results.first().title)
        assertEquals("https://example.com/diagnostics", results.first().url)
        assertEquals("Internal source content", results.first().contentSnippet)
        assertEquals(320, results.first().wordCount)
    }

    @Test
    fun `filters excluded source ids`() {
        val userId = UUID.randomUUID()
        val excludedSourceId = UUID.randomUUID()
        val keptSourceId = UUID.randomUUID()
        val request = SourceSearchRequest(
            userId = userId,
            query = "topic",
            limit = 2,
            excludeSourceIds = setOf(excludedSourceId)
        )

        whenever(sourceSimilarityService.findSimilarSources(userId, "topic", 3, null)).thenReturn(
            listOf(
                SimilarSourceResult(excludedSourceId, 0.91, null, "https://example.com/excluded", 100),
                SimilarSourceResult(keptSourceId, 0.85, null, "https://example.com/kept", 200)
            )
        )
        whenever(sourceRepository.findAllByUserIdAndIdIn(eq(userId), eq(listOf(keptSourceId)))).thenReturn(
            listOf(
                sourceWithContent(
                    sourceId = keptSourceId,
                    userId = userId,
                    title = null,
                    url = "https://example.com/kept",
                    text = ""
                )
            )
        )

        val results = service.search(request)

        assertEquals(1, results.size)
        assertEquals(keptSourceId, results.first().sourceId)
        assertNull(results.first().contentSnippet)
    }

    @Test
    fun `returns empty results for unsupported mode`() {
        val request = SourceSearchRequest(
            userId = UUID.randomUUID(),
            query = "topic cluster",
            mode = SourceSearchMode.TOPIC,
            limit = 5
        )

        val results = service.search(request)

        assertTrue(results.isEmpty())
        verify(sourceSimilarityService, never()).findSimilarSources(any(), any(), any(), any())
    }

    private fun sourceWithContent(
        sourceId: UUID,
        userId: UUID,
        title: String?,
        url: String,
        text: String
    ): Source {
        val source = Source.create(sourceId, url, userId)
        source.startExtraction()
        source.completeExtraction(
            content = Content.from(text),
            metadata = Metadata.from(
                title = title,
                author = null,
                publishedDate = Instant.now(),
                platform = "web",
                wordCount = Content.countWords(text),
                aiFormatted = false,
                extractionProvider = "manual"
            )
        )
        return source
    }
}
