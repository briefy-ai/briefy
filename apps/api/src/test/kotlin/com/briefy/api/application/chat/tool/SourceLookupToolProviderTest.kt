package com.briefy.api.application.chat.tool

import com.briefy.api.application.enrichment.SimilarSourceResult
import com.briefy.api.application.enrichment.SourceSimilarityService
import com.briefy.api.application.source.SourceSortStrategy
import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.domain.knowledgegraph.source.SourceType
import com.briefy.api.domain.knowledgegraph.source.Url
import com.briefy.api.domain.knowledgegraph.topiclink.SourceActiveTopicProjection
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkRepository
import com.briefy.api.infrastructure.security.CurrentUserProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class SourceLookupToolProviderTest {

    private val sourceRepository = mock<SourceRepository>()
    private val sourceSimilarityService = mock<SourceSimilarityService>()
    private val topicLinkRepository = mock<TopicLinkRepository>()
    private val currentUserProvider = mock<CurrentUserProvider>()
    private val tool = SourceLookupToolProvider(
        sourceRepository = sourceRepository,
        sourceSimilarityService = sourceSimilarityService,
        topicLinkRepository = topicLinkRepository,
        currentUserProvider = currentUserProvider
    )

    @Test
    fun `list mode returns active sources with default limit`() {
        val userId = UUID.randomUUID()
        val source = source(title = "AI memo", sourceType = SourceType.BLOG, text = "hello world")
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(
            sourceRepository.findSourcesPage(
                userId = eq(userId),
                status = eq(SourceStatus.ACTIVE),
                topicIds = isNull(),
                sourceType = isNull(),
                sort = eq(SourceSortStrategy.NEWEST),
                cursor = isNull(),
                limit = eq(20)
            )
        ).thenReturn(listOf(source))

        val result = tool.lookup(SourceLookupRequest())

        assertTrue(result is SourceList)
        val list = result as SourceList
        assertEquals(1, list.sources.size)
        assertEquals(source.id, list.sources.first().id)
        assertEquals("blog", list.sources.first().type)
        assertEquals(2, list.sources.first().wordCount)
        assertFalse(list.truncated)
        assertNull(list.hint)
    }

    @Test
    fun `list mode filters by sourceType`() {
        val userId = UUID.randomUUID()
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(
            sourceRepository.findSourcesPage(
                userId = eq(userId),
                status = eq(SourceStatus.ACTIVE),
                topicIds = isNull(),
                sourceType = eq(SourceType.RESEARCH),
                sort = eq(SourceSortStrategy.NEWEST),
                cursor = isNull(),
                limit = eq(20)
            )
        ).thenReturn(emptyList())

        val result = tool.lookup(SourceLookupRequest(sourceType = "research"))

        assertTrue(result is SourceList)
        verify(sourceRepository).findSourcesPage(
            userId = eq(userId),
            status = eq(SourceStatus.ACTIVE),
            topicIds = isNull(),
            sourceType = eq(SourceType.RESEARCH),
            sort = eq(SourceSortStrategy.NEWEST),
            cursor = isNull(),
            limit = eq(20)
        )
    }

    @Test
    fun `list mode filters by topicId`() {
        val userId = UUID.randomUUID()
        val topicId = UUID.randomUUID()
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(
            sourceRepository.findSourcesPage(
                userId = eq(userId),
                status = eq(SourceStatus.ACTIVE),
                topicIds = eq(listOf(topicId)),
                sourceType = isNull(),
                sort = eq(SourceSortStrategy.NEWEST),
                cursor = isNull(),
                limit = eq(20)
            )
        ).thenReturn(emptyList())

        val result = tool.lookup(SourceLookupRequest(topicId = topicId))

        assertTrue(result is SourceList)
        verify(sourceRepository).findSourcesPage(
            userId = eq(userId),
            status = eq(SourceStatus.ACTIVE),
            topicIds = eq(listOf(topicId)),
            sourceType = isNull(),
            sort = eq(SourceSortStrategy.NEWEST),
            cursor = isNull(),
            limit = eq(20)
        )
    }

    @Test
    fun `list mode with text filter uses search then batch fetch`() {
        val userId = UUID.randomUUID()
        val sourceA = source(title = "First", sourceType = SourceType.NEWS, text = "alpha beta")
        val sourceB = source(title = "Second", sourceType = SourceType.BLOG, text = "gamma delta")
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(
            sourceRepository.searchSources(
                userId = userId,
                query = "policy",
                limit = 21,
                topicIds = null,
                sourceType = null
            )
        ).thenReturn(
            listOf(
                searchProjection(sourceA.id, "First", SourceType.NEWS),
                searchProjection(sourceB.id, "Second", SourceType.BLOG)
            )
        )
        whenever(sourceRepository.findAllByUserIdAndIdIn(userId, listOf(sourceA.id, sourceB.id))).thenReturn(
            listOf(sourceB, sourceA)
        )

        val result = tool.lookup(SourceLookupRequest(filter = "policy"))

        assertTrue(result is SourceList)
        val list = result as SourceList
        assertEquals(listOf(sourceA.id, sourceB.id), list.sources.map { it.id })
        verify(sourceRepository).searchSources(
            userId = userId,
            query = "policy",
            limit = 21,
            topicIds = null,
            sourceType = null
        )
        verify(sourceRepository).findAllByUserIdAndIdIn(userId, listOf(sourceA.id, sourceB.id))
    }

    @Test
    fun `list mode with text filter forwards topic and source type filters to search and skips sentinel hydration`() {
        val userId = UUID.randomUUID()
        val topicId = UUID.randomUUID()
        val sourceA = source(title = "First", sourceType = SourceType.RESEARCH, text = "alpha beta")
        val sentinelId = UUID.randomUUID()
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(
            sourceRepository.searchSources(
                userId = userId,
                query = "policy",
                limit = 2,
                topicIds = listOf(topicId),
                sourceType = SourceType.RESEARCH
            )
        ).thenReturn(
            listOf(
                searchProjection(sourceA.id, "First", SourceType.RESEARCH),
                searchProjection(sentinelId, "Sentinel", SourceType.RESEARCH)
            )
        )
        whenever(sourceRepository.findAllByUserIdAndIdIn(userId, listOf(sourceA.id))).thenReturn(
            listOf(sourceA)
        )

        val result = tool.lookup(
            SourceLookupRequest(
                filter = "policy",
                topicId = topicId,
                sourceType = "research",
                limit = 1
            )
        )

        assertTrue(result is SourceList)
        val list = result as SourceList
        assertEquals(listOf(sourceA.id), list.sources.map { it.id })
        assertTrue(list.truncated)
        verify(sourceRepository).searchSources(
            userId = userId,
            query = "policy",
            limit = 2,
            topicIds = listOf(topicId),
            sourceType = SourceType.RESEARCH
        )
        verify(sourceRepository).findAllByUserIdAndIdIn(userId, listOf(sourceA.id))
    }

    @Test
    fun `list mode truncates at 50 and adds hint`() {
        val userId = UUID.randomUUID()
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(
            sourceRepository.findSourcesPage(
                userId = eq(userId),
                status = eq(SourceStatus.ACTIVE),
                topicIds = isNull(),
                sourceType = isNull(),
                sort = eq(SourceSortStrategy.NEWEST),
                cursor = isNull(),
                limit = eq(50)
            )
        ).thenReturn((1..51).map { index -> source(title = "Source $index", text = "text $index") })

        val result = tool.lookup(SourceLookupRequest(limit = 999))

        assertTrue(result is SourceList)
        val list = result as SourceList
        assertEquals(50, list.sources.size)
        assertTrue(list.truncated)
        assertEquals("Results truncated to 50 sources. Narrow the search with 'filter', 'sourceType', or 'topicId'.", list.hint)
    }

    @Test
    fun `get mode returns source detail with topics`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        val topicId = UUID.randomUUID()
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.findByIdAndUserId(sourceId, userId)).thenReturn(
            source(
                id = sourceId,
                title = "Policy memo",
                author = "Author",
                platform = "web",
                sourceType = SourceType.RESEARCH,
                text = "a b c",
                isRead = true
            )
        )
        whenever(topicLinkRepository.findActiveTopicsBySourceIds(userId, listOf(sourceId))).thenReturn(
            listOf(activeTopicProjection(sourceId, topicId, "AI policy"))
        )

        val result = tool.lookup(SourceLookupRequest(sourceId = sourceId))

        assertTrue(result is SourceDetail)
        val detail = result as SourceDetail
        assertEquals(sourceId, detail.id)
        assertEquals("Policy memo", detail.title)
        assertEquals("research", detail.sourceType)
        assertEquals(3, detail.wordCount)
        assertTrue(detail.isRead)
        assertEquals(1, detail.topics.size)
        assertEquals("AI policy", detail.topics.first().name)
    }

    @Test
    fun `get mode returns error for missing source`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.findByIdAndUserId(sourceId, userId)).thenReturn(null)

        val result = tool.lookup(SourceLookupRequest(sourceId = sourceId))

        assertTrue(result is SourceLookupError)
        assertEquals("Source not found for source_lookup.", (result as SourceLookupError).message)
    }

    @Test
    fun `content mode returns truncated text when over limit`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        val longText = (1..3001).joinToString(" ") { "word$it" }
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.findByIdAndUserId(sourceId, userId)).thenReturn(
            source(id = sourceId, title = "Long source", text = longText)
        )

        val result = tool.lookup(SourceLookupRequest(sourceId = sourceId, includeContent = true))

        assertTrue(result is SourceContent)
        val content = result as SourceContent
        assertTrue(content.truncated)
        assertEquals(3001, content.wordCount)
        assertEquals(3000, Content.countWords(content.content))
    }

    @Test
    fun `content mode returns full text when under limit`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.findByIdAndUserId(sourceId, userId)).thenReturn(
            source(id = sourceId, title = "Short source", text = "alpha beta gamma")
        )

        val result = tool.lookup(SourceLookupRequest(sourceId = sourceId, includeContent = true))

        assertTrue(result is SourceContent)
        val content = result as SourceContent
        assertFalse(content.truncated)
        assertEquals("alpha beta gamma", content.content)
    }

    @Test
    fun `content mode returns error when no content`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.findByIdAndUserId(sourceId, userId)).thenReturn(
            source(id = sourceId, title = "Empty source", content = null)
        )

        val result = tool.lookup(SourceLookupRequest(sourceId = sourceId, includeContent = true))

        assertTrue(result is SourceLookupError)
        assertEquals("Source content not available for source_lookup.", (result as SourceLookupError).message)
    }

    @Test
    fun `search mode performs semantic search`() {
        val userId = UUID.randomUUID()
        val matchId = UUID.randomUUID()
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceSimilarityService.findSimilarSources(userId, "ai policy", 20)).thenReturn(
            listOf(
                SimilarSourceResult(
                    sourceId = matchId,
                    score = 0.98,
                    title = "Policy memo",
                    url = "https://example.com/memo",
                    wordCount = 1200
                )
            )
        )

        val result = tool.lookup(SourceLookupRequest(query = "ai policy"))

        assertTrue(result is SourceSearchResults)
        val searchResults = result as SourceSearchResults
        assertEquals(1, searchResults.results.size)
        assertEquals(matchId, searchResults.results.first().id)
        assertEquals(0.98, searchResults.results.first().score)
    }

    @Test
    fun `similar mode finds similar sources`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        val matchId = UUID.randomUUID()
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.findByIdAndUserId(sourceId, userId)).thenReturn(
            source(id = sourceId, title = "Anchor", text = "anchor text")
        )
        whenever(
            sourceSimilarityService.findSimilarSourcesBySourceId(
                userId = userId,
                sourceId = sourceId,
                limit = 20,
                excludeSourceIds = setOf(sourceId)
            )
        ).thenReturn(
            listOf(
                SimilarSourceResult(
                    sourceId = matchId,
                    score = 0.88,
                    title = "Related memo",
                    url = "https://example.com/related",
                    wordCount = 640
                )
            )
        )

        val result = tool.lookup(SourceLookupRequest(sourceId = sourceId, findSimilar = true))

        assertTrue(result is SourceSearchResults)
        val searchResults = result as SourceSearchResults
        assertEquals(1, searchResults.results.size)
        assertEquals(matchId, searchResults.results.first().id)
    }

    @Test
    fun `similar mode returns error for missing source`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.findByIdAndUserId(sourceId, userId)).thenReturn(null)

        val result = tool.lookup(SourceLookupRequest(sourceId = sourceId, findSimilar = true))

        assertTrue(result is SourceLookupError)
        assertEquals("Source not found for source_lookup.", (result as SourceLookupError).message)
    }

    private fun source(
        id: UUID = UUID.randomUUID(),
        title: String = "Source",
        author: String = "Author",
        platform: String = "web",
        sourceType: SourceType = SourceType.BLOG,
        text: String = "hello world",
        isRead: Boolean = false,
        content: Content? = Content.from(text)
    ): Source {
        return Source(
            id = id,
            url = Url.from("https://example.com/$id"),
            status = SourceStatus.ACTIVE,
            content = content,
            metadata = Metadata.from(
                title = title,
                author = author,
                publishedDate = Instant.parse("2024-01-15T10:00:00Z"),
                platform = platform,
                wordCount = content?.wordCount ?: 0,
                aiFormatted = false,
                extractionProvider = "manual"
            ),
            sourceType = sourceType,
            userId = UUID.randomUUID(),
            createdAt = Instant.parse("2024-01-10T10:00:00Z"),
            updatedAt = Instant.parse("2024-01-11T10:00:00Z"),
            isRead = isRead
        )
    }

    private fun searchProjection(id: UUID, title: String, sourceType: SourceType) =
        com.briefy.api.domain.knowledgegraph.source.SourceSearchProjection(
            id = id,
            title = title,
            author = "Author",
            domain = "example.com",
            sourceType = sourceType
        )

    private fun activeTopicProjection(rawSourceId: UUID, rawTopicId: UUID, rawTopicName: String) = object : SourceActiveTopicProjection {
        override val sourceId: UUID = rawSourceId
        override val topicId: UUID = rawTopicId
        override val topicName: String = rawTopicName
    }
}
