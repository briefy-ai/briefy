package com.briefy.api.application.briefing.tool

import com.briefy.api.application.enrichment.SimilarSourceResult
import com.briefy.api.application.enrichment.SourceSimilarityService
import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.domain.knowledgegraph.source.Url
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

class SourceLookupToolProviderTest {

    private val sourceSimilarityService = mock<SourceSimilarityService>()
    private val sourceRepository = mock<SourceRepository>()
    private val tool = SourceLookupToolProvider(sourceSimilarityService, sourceRepository)

    @Test
    fun `query mode returns hydrated results with excerpts`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        whenever(
            sourceSimilarityService.findSimilarSources(
                userId = userId,
                query = "vector databases",
                limit = 5,
                excludeSourceIds = setOf(UUID.fromString("00000000-0000-0000-0000-000000000001"))
            )
        ).thenReturn(
            listOf(
                SimilarSourceResult(
                    sourceId = sourceId,
                    score = 0.91,
                    title = "Vector DB Notes",
                    url = "https://example.com/vector-db",
                    wordCount = 420
                )
            )
        )
        whenever(sourceRepository.findAllByUserIdAndIdIn(userId, listOf(sourceId))).thenReturn(
            listOf(source(userId, sourceId, "Vector DB Notes", "A long excerpt about vector databases"))
        )

        val result = tool.lookup(
            query = "vector databases",
            sourceId = null,
            limit = 5,
            userId = userId,
            excludeSourceIds = setOf(UUID.fromString("00000000-0000-0000-0000-000000000001"))
        )

        assertTrue(result is ToolResult.Success)
        val response = (result as ToolResult.Success).data
        assertEquals("query", response.mode)
        assertEquals(1, response.results.size)
        assertTrue(response.results.first().excerpt!!.contains("vector databases"))
        verify(sourceSimilarityService, never()).findSimilarSourcesBySourceId(any(), any(), any(), any())
    }

    @Test
    fun `source mode requires explicit sourceId and uses stored embedding path`() {
        val userId = UUID.randomUUID()
        val anchorId = UUID.randomUUID()
        val matchId = UUID.randomUUID()
        whenever(
            sourceSimilarityService.findSimilarSourcesBySourceId(
                userId = userId,
                sourceId = anchorId,
                limit = 3,
                excludeSourceIds = setOf(anchorId)
            )
        ).thenReturn(
            listOf(
                SimilarSourceResult(
                    sourceId = matchId,
                    score = 0.77,
                    title = "Related Source",
                    url = "https://example.com/related",
                    wordCount = 250
                )
            )
        )
        whenever(sourceRepository.findAllByUserIdAndIdIn(userId, listOf(matchId))).thenReturn(
            listOf(source(userId, matchId, "Related Source", "Stored-embedding match excerpt"))
        )

        val result = tool.lookup(
            query = null,
            sourceId = anchorId,
            limit = 3,
            userId = userId,
            excludeSourceIds = setOf(anchorId)
        )

        assertTrue(result is ToolResult.Success)
        val response = (result as ToolResult.Success).data
        assertEquals("source", response.mode)
        assertEquals(anchorId, response.sourceId)
        verify(sourceSimilarityService).findSimilarSourcesBySourceId(
            userId = userId,
            sourceId = anchorId,
            limit = 3,
            excludeSourceIds = setOf(anchorId)
        )
        verify(sourceSimilarityService, never()).findSimilarSources(any(), any(), any(), any())
    }

    @Test
    fun `returns parse error when both query and sourceId are missing`() {
        val result = tool.lookup(
            query = null,
            sourceId = null,
            limit = 5,
            userId = UUID.randomUUID(),
            excludeSourceIds = emptySet()
        )

        assertTrue(result is ToolResult.Error)
        assertEquals(ToolErrorCode.PARSE_ERROR, (result as ToolResult.Error).code)
    }

    @Test
    fun `returns null excerpt when hydrated source is missing`() {
        val userId = UUID.randomUUID()
        val matchId = UUID.randomUUID()
        whenever(
            sourceSimilarityService.findSimilarSources(
                userId = userId,
                query = "query",
                limit = 5,
                excludeSourceIds = emptySet()
            )
        ).thenReturn(
            listOf(
                SimilarSourceResult(
                    sourceId = matchId,
                    score = 0.5,
                    title = "Missing hydrated source",
                    url = "https://example.com/missing",
                    wordCount = 10
                )
            )
        )
        whenever(sourceRepository.findAllByUserIdAndIdIn(userId, listOf(matchId))).thenReturn(emptyList())

        val result = tool.lookup(
            query = "query",
            sourceId = null,
            limit = 5,
            userId = userId,
            excludeSourceIds = emptySet()
        )

        assertTrue(result is ToolResult.Success)
        assertNull((result as ToolResult.Success).data.results.first().excerpt)
    }

    private fun source(userId: UUID, sourceId: UUID, title: String, text: String): Source {
        return Source(
            id = sourceId,
            url = Url.from("https://example.com/$sourceId"),
            status = SourceStatus.ACTIVE,
            content = Content.from(text),
            metadata = Metadata.from(
                title = title,
                author = "Author",
                publishedDate = Instant.parse("2025-01-01T00:00:00Z"),
                platform = "web",
                wordCount = 42,
                aiFormatted = true,
                extractionProvider = "jsoup"
            ),
            userId = userId,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
}
