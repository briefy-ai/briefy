package com.briefy.api.infrastructure.mcp.tools

import com.briefy.api.domain.knowledgegraph.briefing.BriefingStatus
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.domain.knowledgegraph.topic.Topic
import com.briefy.api.domain.knowledgegraph.topic.TopicRepository
import com.briefy.api.domain.knowledgegraph.topic.TopicStatus
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkCountProjection
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkRepository
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkStatus
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkTargetType
import com.briefy.api.infrastructure.mcp.McpJson
import com.briefy.api.infrastructure.security.OAuthPrincipal
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant
import java.util.UUID

class GetTopicsToolTest {

    private val topicRepository = mock<TopicRepository>()
    private val topicLinkRepository = mock<TopicLinkRepository>()
    private val objectMapper = jacksonObjectMapper().findAndRegisterModules()
    private val tool = GetTopicsTool(topicRepository, topicLinkRepository, McpJson(objectMapper))
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setupSecurityContext() {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            OAuthPrincipal(userId, listOf("mcp:read")),
            null,
            emptyList()
        )
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `default most recent only hydrates counts for limited topics`() {
        val newest = topic("Newest", updatedAt = "2026-01-03T00:00:00Z")
        val middle = topic("Middle", updatedAt = "2026-01-02T00:00:00Z")
        val oldest = topic("Oldest", updatedAt = "2026-01-01T00:00:00Z")
        whenever(topicRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(userId, TopicStatus.ACTIVE))
            .thenReturn(listOf(newest, middle, oldest))
        whenever(countSources(listOf(newest.id, middle.id))).thenReturn(emptyList())
        whenever(countBriefings(listOf(newest.id, middle.id))).thenReturn(emptyList())

        val payload = tool.toolCallback().call("""{"limit":2}""")
        val items = objectMapper.readTree(payload)

        assertEquals(listOf("Newest", "Middle"), items.map { it.get("name").asText() })
        verify(topicLinkRepository).countByTopicIdsAndStatusAndSourceStatus(
            userId,
            listOf(newest.id, middle.id),
            TopicLinkTargetType.SOURCE,
            TopicLinkStatus.ACTIVE,
            SourceStatus.ACTIVE
        )
    }

    @Test
    fun `most frequent tiebreaks by updated date not briefing count`() {
        val newer = topic("Newer", updatedAt = "2026-01-03T00:00:00Z")
        val olderWithBriefings = topic("Older with briefings", updatedAt = "2026-01-02T00:00:00Z")
        whenever(topicRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(userId, TopicStatus.ACTIVE))
            .thenReturn(listOf(newer, olderWithBriefings))
        whenever(countSources(listOf(newer.id, olderWithBriefings.id))).thenReturn(
            listOf(
                countProjection(newer.id, 2),
                countProjection(olderWithBriefings.id, 2)
            )
        )
        whenever(countBriefings(listOf(newer.id, olderWithBriefings.id))).thenReturn(
            listOf(countProjection(olderWithBriefings.id, 20))
        )

        val payload = tool.toolCallback().call("""{"limit":2,"orderBy":"most_frequent"}""")
        val items = objectMapper.readTree(payload)

        assertEquals(listOf("Newer", "Older with briefings"), items.map { it.get("name").asText() })
    }

    private fun countSources(topicIds: List<UUID>) = topicLinkRepository.countByTopicIdsAndStatusAndSourceStatus(
        userId,
        topicIds,
        TopicLinkTargetType.SOURCE,
        TopicLinkStatus.ACTIVE,
        SourceStatus.ACTIVE
    )

    private fun countBriefings(topicIds: List<UUID>) = topicLinkRepository.countByTopicIdsAndStatusAndBriefingStatus(
        userId,
        topicIds,
        TopicLinkTargetType.BRIEFING,
        TopicLinkStatus.ACTIVE,
        BriefingStatus.READY
    )

    private fun topic(name: String, updatedAt: String) = Topic(
        id = UUID.randomUUID(),
        name = name,
        nameNormalized = Topic.normalizeName(name),
        status = TopicStatus.ACTIVE,
        userId = userId,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse(updatedAt)
    )

    private fun countProjection(rawTopicId: UUID, rawLinkCount: Long) = object : TopicLinkCountProjection {
        override val topicId: UUID = rawTopicId
        override val linkCount: Long = rawLinkCount
    }
}
