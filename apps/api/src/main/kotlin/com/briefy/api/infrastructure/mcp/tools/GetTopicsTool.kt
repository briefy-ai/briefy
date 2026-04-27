package com.briefy.api.infrastructure.mcp.tools

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
import java.util.UUID
import java.util.function.Function

@Component
class GetTopicsTool(
    private val topicRepository: TopicRepository,
    private val topicLinkRepository: TopicLinkRepository,
    private val mcpJson: McpJson,
) {
    data class Input(val query: String? = null, val limit: Int? = null)

    data class Item(
        val id: UUID,
        val name: String,
        val sourceCount: Long,
        val briefingCount: Long,
        val takeawayCount: Long,
    )

    fun toolCallback(): ToolCallback = FunctionToolCallback.builder(
        "get_topics",
        Function<Input, String> { execute(it) }
    )
        .description("List the user's confirmed topics, optionally filtered by name substring. Returns each topic with id, name, and counts of linked sources, briefings, and takeaways.")
        .inputType(Input::class.java)
        .build()

    private fun execute(input: Input): String {
        val userId = CurrentMcpUser.userId()
        val limit = (input.limit ?: 20).coerceIn(1, 50)
        val query = input.query?.trim()?.takeIf { it.isNotEmpty() }

        val topics: List<Topic> = if (query != null) {
            topicRepository.findByUserIdAndStatusAndNameContainingIgnoreCaseOrderByUpdatedAtDesc(
                userId, TopicStatus.ACTIVE, query
            )
        } else {
            topicRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(userId, TopicStatus.ACTIVE)
        }.take(limit)

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
            )
        }
        return mcpJson.stringify(items)
    }
}
