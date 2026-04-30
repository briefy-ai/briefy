package com.briefy.api.infrastructure.mcp.tools

import com.briefy.api.application.topic.TopicSort
import com.briefy.api.domain.knowledgegraph.briefing.BriefingStatus
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.domain.knowledgegraph.topic.Topic
import com.briefy.api.domain.knowledgegraph.topic.TopicRepository
import com.briefy.api.domain.knowledgegraph.topic.TopicStatus
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkRepository
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkStatus
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkTargetType
import com.briefy.api.infrastructure.mcp.CurrentMcpUser
import com.briefy.api.infrastructure.mcp.McpJson
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.function.FunctionToolCallback
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.function.Function

@Component
class GetTopicsTool(
    private val topicRepository: TopicRepository,
    private val topicLinkRepository: TopicLinkRepository,
    private val mcpJson: McpJson,
) {
    data class Input(
        val query: String? = null,
        val limit: Int? = null,
        val orderBy: TopicSort? = null
    )

    data class Item(
        val id: UUID,
        val name: String,
        val sourceCount: Long,
        val briefingCount: Long,
        val takeawayCount: Long,
        val createdAt: Instant,
        val updatedAt: Instant,
    )

    fun toolCallback(): ToolCallback = FunctionToolCallback.builder(
        "get_topics",
        Function<Input, String> { execute(it) }
    )
        .description("List the user's confirmed topics, optionally filtered by name substring. orderBy accepts most_frequent, most_recent, newly_created, or oldest. Use most_frequent for questions about what the user reads most. Returns each topic with id, name, and counts of linked sources, briefings, and takeaways.")
        .inputType(Input::class.java)
        .build()

    private fun execute(input: Input): String {
        val userId = CurrentMcpUser.userId()
        val limit = (input.limit ?: 20).coerceIn(1, 50)
        val query = input.query?.trim()?.takeIf { it.isNotEmpty() }
        val sort = input.orderBy ?: TopicSort.DEFAULT

        val topics: List<Topic> = if (query != null) {
            topicRepository.findByUserIdAndStatusAndNameContainingIgnoreCaseOrderByUpdatedAtDesc(
                userId, TopicStatus.ACTIVE, query
            )
        } else {
            topicRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(userId, TopicStatus.ACTIVE)
        }

        if (topics.isEmpty()) return mcpJson.stringify(emptyList<Item>())

        val topicIds = topics.map { it.id }
        val sourceCounts = topicLinkRepository.countByTopicIdsAndStatusAndSourceStatus(
            userId, topicIds, TopicLinkTargetType.SOURCE, TopicLinkStatus.ACTIVE, SourceStatus.ACTIVE
        ).associate { it.topicId to it.linkCount }
        val briefingCounts = topicLinkRepository.countByTopicIdsAndStatusAndBriefingStatus(
            userId, topicIds, TopicLinkTargetType.BRIEFING, TopicLinkStatus.ACTIVE, BriefingStatus.READY
        ).associate { it.topicId to it.linkCount }

        val items = topics.map { topic ->
            Item(
                id = topic.id,
                name = topic.name,
                sourceCount = sourceCounts[topic.id] ?: 0L,
                briefingCount = briefingCounts[topic.id] ?: 0L,
                takeawayCount = 0L,
                createdAt = topic.createdAt,
                updatedAt = topic.updatedAt,
            )
        }.sorted(sort).take(limit)
        return mcpJson.stringify(items)
    }

    private fun List<Item>.sorted(sort: TopicSort): List<Item> {
        return when (sort) {
            TopicSort.MOST_FREQUENT -> sortedWith(
                compareByDescending<Item> { it.sourceCount }
                    .thenByDescending { it.briefingCount }
                    .thenByDescending { it.updatedAt }
                    .thenBy { it.name.lowercase() }
            )
            TopicSort.MOST_RECENT -> sortedWith(
                compareByDescending<Item> { it.updatedAt }
                    .thenBy { it.name.lowercase() }
            )
            TopicSort.NEWLY_CREATED -> sortedWith(
                compareByDescending<Item> { it.createdAt }
                    .thenBy { it.name.lowercase() }
            )
            TopicSort.OLDEST -> sortedWith(
                compareBy<Item> { it.createdAt }
                    .thenBy { it.name.lowercase() }
            )
        }
    }
}
