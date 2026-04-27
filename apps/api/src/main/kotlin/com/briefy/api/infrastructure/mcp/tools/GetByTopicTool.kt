package com.briefy.api.infrastructure.mcp.tools

import com.briefy.api.domain.knowledgegraph.briefing.BriefingReferenceRepository
import com.briefy.api.domain.knowledgegraph.briefing.BriefingReferenceStatus
import com.briefy.api.domain.knowledgegraph.briefing.BriefingRepository
import com.briefy.api.domain.knowledgegraph.briefing.BriefingSourceRepository
import com.briefy.api.domain.knowledgegraph.briefing.BriefingStatus
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.domain.knowledgegraph.topic.TopicRepository
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
class GetByTopicTool(
    private val topicRepository: TopicRepository,
    private val topicLinkRepository: TopicLinkRepository,
    private val sourceRepository: SourceRepository,
    private val briefingRepository: BriefingRepository,
    private val briefingSourceRepository: BriefingSourceRepository,
    private val briefingReferenceRepository: BriefingReferenceRepository,
    private val mcpJson: McpJson,
) {
    data class Input(val topicId: String, val types: List<String>, val limit: Int? = null)

    data class SourceItem(
        val id: UUID,
        val title: String?,
        val author: String?,
        val platform: String?,
        val url: String,
        val excerpt: String?,
        val publishedDate: Instant?,
    )

    data class Reference(val url: String, val title: String, val snippet: String?)
    data class BriefingItem(
        val id: UUID,
        val title: String?,
        val synthesizedText: String?,
        val sourceIds: List<UUID>,
        val references: List<Reference>,
        val createdAt: Instant,
    )

    fun toolCallback(): ToolCallback = FunctionToolCallback.builder(
        "get_by_topic",
        Function<Input, String> { execute(it) }
    )
        .description("Get sources, briefings, and/or takeaways linked to a specific topic. The 'types' field accepts any combination of 'source', 'briefing', 'takeaway'. Each requested type is returned as its own array under the matching key. (Takeaways are not yet available — that key will always be empty.)")
        .inputType(Input::class.java)
        .build()

    private fun execute(input: Input): String {
        val userId = CurrentMcpUser.userId()
        val topicId = runCatching { UUID.fromString(input.topicId) }.getOrNull()
            ?: return mcpJson.stringify(mapOf("error" to "Invalid topic id"))
        val limit = (input.limit ?: 10).coerceIn(1, 50)

        topicRepository.findByIdAndUserId(topicId, userId)
            ?: return mcpJson.stringify(mapOf("error" to "Topic not found"))

        val requested = input.types.map { it.lowercase() }.toSet()
        val result = mutableMapOf<String, Any>()

        if ("source" in requested) {
            result["sources"] = sourcesForTopic(userId, topicId, limit)
        }
        if ("briefing" in requested) {
            result["briefings"] = briefingsForTopic(userId, topicId, limit)
        }
        if ("takeaway" in requested) {
            result["takeaways"] = emptyList<Any>()
        }

        return mcpJson.stringify(result)
    }

    private fun sourcesForTopic(userId: UUID, topicId: UUID, limit: Int): List<SourceItem> {
        val links = topicLinkRepository.findByUserIdAndTopicIdAndTargetTypeAndStatusAndSourceStatusOrderByAssignedAtDesc(
            userId, topicId, TopicLinkTargetType.SOURCE, TopicLinkStatus.ACTIVE, SourceStatus.ACTIVE
        ).take(limit)
        if (links.isEmpty()) return emptyList()
        val ids = links.map { it.targetId }
        val byId = sourceRepository.findAllByUserIdAndIdIn(userId, ids).associateBy { it.id }
        return ids.mapNotNull { id ->
            val s = byId[id] ?: return@mapNotNull null
            SourceItem(
                id = s.id,
                title = s.metadata?.title,
                author = s.metadata?.author,
                platform = s.url.platform,
                url = s.url.raw,
                excerpt = mcpJson.excerpt(s.content?.text, 300),
                publishedDate = s.metadata?.publishedDate,
            )
        }
    }

    private fun briefingsForTopic(userId: UUID, topicId: UUID, limit: Int): List<BriefingItem> {
        val links = topicLinkRepository
            .findByUserIdAndTopicIdAndTargetTypeAndStatusAndBriefingStatusOrderByAssignedAtDesc(
                userId, topicId, TopicLinkTargetType.BRIEFING, TopicLinkStatus.ACTIVE, BriefingStatus.READY
            )
            .take(limit)
        if (links.isEmpty()) return emptyList()
        val ids = links.map { it.targetId }
        val byId = ids.mapNotNull { briefingRepository.findByIdAndUserId(it, userId) }.associateBy { it.id }

        val sourcesByBriefing = briefingSourceRepository.findByBriefingIdInOrderByCreatedAtAsc(ids)
            .groupBy({ it.briefingId }, { it.sourceId })
        val refsByBriefing = briefingReferenceRepository
            .findByBriefingIdInAndStatusOrderByCreatedAtAsc(ids, BriefingReferenceStatus.ACTIVE)
            .groupBy { it.briefingId }

        return ids.mapNotNull { id ->
            val b = byId[id] ?: return@mapNotNull null
            BriefingItem(
                id = b.id,
                title = b.title,
                synthesizedText = mcpJson.excerpt(b.contentMarkdown, 500),
                sourceIds = sourcesByBriefing[b.id].orEmpty(),
                references = refsByBriefing[b.id].orEmpty()
                    .map { Reference(url = it.url, title = it.title, snippet = it.snippet) },
                createdAt = b.createdAt,
            )
        }
    }
}
