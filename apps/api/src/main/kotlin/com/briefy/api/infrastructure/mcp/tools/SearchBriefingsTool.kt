package com.briefy.api.infrastructure.mcp.tools

import com.briefy.api.domain.knowledgegraph.briefing.BriefingReferenceRepository
import com.briefy.api.domain.knowledgegraph.briefing.BriefingReferenceStatus
import com.briefy.api.domain.knowledgegraph.briefing.BriefingSearchRepository
import com.briefy.api.domain.knowledgegraph.briefing.BriefingSourceRepository
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
class SearchBriefingsTool(
    private val briefingSearchRepository: BriefingSearchRepository,
    private val briefingReferenceRepository: BriefingReferenceRepository,
    private val briefingSourceRepository: BriefingSourceRepository,
    private val topicLinkRepository: TopicLinkRepository,
    private val topicRepository: TopicRepository,
    private val mcpJson: McpJson,
) {
    data class Input(val query: String, val topicId: String? = null, val limit: Int? = null)

    data class Reference(val url: String, val title: String, val snippet: String?)
    data class Item(
        val id: UUID,
        val title: String?,
        val synthesizedText: String?,
        val sourceIds: List<UUID>,
        val references: List<Reference>,
        val topics: List<String>,
        val createdAt: Instant,
    )

    fun toolCallback(): ToolCallback = FunctionToolCallback.builder(
        "search_briefings",
        Function<Input, String> { execute(it) }
    )
        .description("Substring search over the user's briefings (synthesized multi-source perspectives). Matches on title and synthesized text. Returns each briefing's title, a 500-char excerpt of its synthesized text, source IDs, citation references, and topics.")
        .inputType(Input::class.java)
        .build()

    private fun execute(input: Input): String {
        val userId = CurrentMcpUser.userId()
        val limit = (input.limit ?: 5).coerceIn(1, 10)
        val topicId = input.topicId?.takeIf { it.isNotBlank() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val query = input.query.trim()
        if (query.isEmpty()) return mcpJson.stringify(emptyList<Item>())

        val hits = briefingSearchRepository.searchReady(userId, query, topicId, limit)
        if (hits.isEmpty()) return mcpJson.stringify(emptyList<Item>())

        val items = hits.map { hit ->
            val sourceIds = briefingSourceRepository.findByBriefingIdOrderByCreatedAtAsc(hit.id).map { it.sourceId }
            val references = briefingReferenceRepository.findByBriefingIdOrderByCreatedAtAsc(hit.id)
                .filter { it.status == BriefingReferenceStatus.ACTIVE }
                .map { Reference(url = it.url, title = it.title, snippet = it.snippet) }
            val topicNames = topicNamesForBriefing(userId, hit.id)
            Item(
                id = hit.id,
                title = hit.title,
                synthesizedText = mcpJson.excerpt(hit.contentMarkdown, 500),
                sourceIds = sourceIds,
                references = references,
                topics = topicNames,
                createdAt = hit.createdAt,
            )
        }

        return mcpJson.stringify(items)
    }

    private fun topicNamesForBriefing(userId: UUID, briefingId: UUID): List<String> {
        val links = topicLinkRepository.findByUserIdAndTargetTypeAndTargetIdAndStatusOrderByAssignedAtDesc(
            userId, TopicLinkTargetType.BRIEFING, briefingId, TopicLinkStatus.ACTIVE
        )
        if (links.isEmpty()) return emptyList()
        val topicIds = links.map { it.topicId }.distinct()
        return topicRepository.findAllByIdInAndUserId(topicIds, userId).map { it.name }
    }
}
