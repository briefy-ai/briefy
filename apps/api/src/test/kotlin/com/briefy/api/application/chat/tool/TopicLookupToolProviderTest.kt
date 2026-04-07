package com.briefy.api.application.chat.tool

import com.briefy.api.application.topic.TopicNotFoundException
import com.briefy.api.application.topic.TopicService
import com.briefy.api.application.topic.TopicSummaryResponse
import com.briefy.api.application.topic.TopicWithSources
import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.domain.knowledgegraph.source.Url
import com.briefy.api.domain.knowledgegraph.topic.Topic
import com.briefy.api.domain.knowledgegraph.topic.TopicStatus
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkRepository
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkStatus
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkTargetType
import com.briefy.api.domain.knowledgegraph.topiclink.TopicSourceIdProjection
import com.briefy.api.infrastructure.security.CurrentUserProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

class TopicLookupToolProviderTest {

    private val topicService = mock<TopicService>()
    private val topicLinkRepository = mock<TopicLinkRepository>()
    private val currentUserProvider = mock<CurrentUserProvider>()
    private val tool = TopicLookupToolProvider(
        topicService = topicService,
        topicLinkRepository = topicLinkRepository,
        currentUserProvider = currentUserProvider
    )

    @Test
    fun `list mode defaults to active topics`() {
        val userId = UUID.randomUUID()
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(topicService.listTopics(TopicStatus.ACTIVE, null)).thenReturn(
            listOf(topicSummary(name = "AI policy", sourceCount = 2))
        )

        val result = tool.lookup(TopicLookupRequest())

        assertTrue(result is TopicList)
        val list = result as TopicList
        assertEquals(1, list.topics.size)
        assertEquals("active", list.topics.first().status)
        assertEquals(2, list.topics.first().sourceCount)
        assertNull(list.topics.first().sourceIds)
        assertFalse(list.truncated)
        verify(topicService).listTopics(TopicStatus.ACTIVE, null)
        verify(topicLinkRepository, never()).findSourceIdsByTopicIds(any(), any(), any(), any(), any())
    }

    @Test
    fun `list mode forwards filter`() {
        whenever(topicService.listTopics(TopicStatus.ACTIVE, "health")).thenReturn(emptyList())

        val result = tool.lookup(TopicLookupRequest(filter = "health"))

        assertTrue(result is TopicList)
        verify(topicService).listTopics(TopicStatus.ACTIVE, "health")
    }

    @Test
    fun `list mode hydrates source ids when requested`() {
        val userId = UUID.randomUUID()
        val topicId = UUID.randomUUID()
        val sourceA = UUID.randomUUID()
        val sourceB = UUID.randomUUID()
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(topicService.listTopics(TopicStatus.ACTIVE, null)).thenReturn(
            listOf(topicSummary(id = topicId, name = "AI policy", sourceCount = 2))
        )
        whenever(
            topicLinkRepository.findSourceIdsByTopicIds(
                userId = userId,
                topicIds = listOf(topicId),
                targetType = TopicLinkTargetType.SOURCE,
                topicLinkStatus = TopicLinkStatus.ACTIVE,
                sourceStatus = SourceStatus.ACTIVE
            )
        ).thenReturn(
            listOf(
                topicSourceIdProjection(topicId, sourceA),
                topicSourceIdProjection(topicId, sourceB)
            )
        )

        val result = tool.lookup(TopicLookupRequest(includeSourceIds = true))

        assertTrue(result is TopicList)
        val list = result as TopicList
        assertEquals(listOf(sourceA, sourceB), list.topics.first().sourceIds)
    }

    @Test
    fun `detail mode returns topic and rich source fields`() {
        val topicId = UUID.randomUUID()
        whenever(topicService.getTopicWithSources(topicId)).thenReturn(
            TopicWithSources(
                topic = topic(topicId, "AI policy", TopicStatus.ACTIVE),
                sources = listOf(source(title = "Policy memo", isRead = true, text = "A detailed memo"))
            )
        )

        val result = tool.lookup(TopicLookupRequest(topicId = topicId))

        assertTrue(result is TopicDetail)
        val detail = result as TopicDetail
        assertEquals("AI policy", detail.name)
        assertEquals("active", detail.status)
        assertEquals(1, detail.sources.size)
        assertEquals("Policy memo", detail.sources.first().title)
        assertTrue(detail.sources.first().isRead)
        assertEquals(Content.countWords("A detailed memo"), detail.sources.first().wordCount)
    }

    @Test
    fun `missing topic returns structured error`() {
        val topicId = UUID.randomUUID()
        whenever(topicService.getTopicWithSources(topicId)).thenThrow(TopicNotFoundException(topicId))

        val result = tool.lookup(TopicLookupRequest(topicId = topicId))

        assertTrue(result is TopicLookupError)
        assertEquals("Topic not found for topic_lookup.", (result as TopicLookupError).message)
    }

    @Test
    fun `invalid status returns structured error`() {
        val result = tool.lookup(TopicLookupRequest(status = "bad"))

        assertTrue(result is TopicLookupError)
        assertEquals(
            "Invalid 'status' argument for topic_lookup. Expected ACTIVE, SUGGESTED, or ARCHIVED.",
            (result as TopicLookupError).message
        )
    }

    @Test
    fun `empty list mode returns empty results`() {
        whenever(topicService.listTopics(TopicStatus.SUGGESTED, null)).thenReturn(emptyList())

        val result = tool.lookup(TopicLookupRequest(status = "suggested"))

        assertTrue(result is TopicList)
        assertTrue((result as TopicList).topics.isEmpty())
    }

    @Test
    fun `list mode truncates to fifty topics and adds hint`() {
        whenever(topicService.listTopics(TopicStatus.ACTIVE, null)).thenReturn(
            (1..51).map { index -> topicSummary(name = "Topic $index", sourceCount = index.toLong()) }
        )

        val result = tool.lookup(TopicLookupRequest())

        assertTrue(result is TopicList)
        val list = result as TopicList
        assertEquals(50, list.topics.size)
        assertTrue(list.truncated)
        assertEquals("Results truncated to 50 topics. Narrow the search with 'filter' or 'status'.", list.hint)
    }

    private fun topicSummary(
        id: UUID = UUID.randomUUID(),
        name: String,
        sourceCount: Long
    ) = TopicSummaryResponse(
        id = id,
        name = name,
        status = "active",
        origin = "user",
        linkedSourcesCount = sourceCount,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-02T00:00:00Z")
    )

    private fun topic(id: UUID, name: String, status: TopicStatus) = Topic(
        id = id,
        name = name,
        nameNormalized = Topic.normalizeName(name),
        status = status,
        userId = UUID.randomUUID(),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-02T00:00:00Z")
    )

    private fun source(title: String, isRead: Boolean, text: String) = Source(
        id = UUID.randomUUID(),
        url = Url.from("https://example.com/source"),
        status = SourceStatus.ACTIVE,
        content = Content.from(text),
        metadata = Metadata.from(
            title = title,
            author = "Author",
            publishedDate = Instant.parse("2026-01-01T00:00:00Z"),
            platform = "web",
            wordCount = Content.countWords(text),
            aiFormatted = true,
            extractionProvider = "manual"
        ),
        userId = UUID.randomUUID(),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-02T00:00:00Z"),
        isRead = isRead
    )

    private fun topicSourceIdProjection(rawTopicId: UUID, rawSourceId: UUID) = object : TopicSourceIdProjection {
        override val topicId: UUID = rawTopicId
        override val sourceId: UUID = rawSourceId
    }
}
