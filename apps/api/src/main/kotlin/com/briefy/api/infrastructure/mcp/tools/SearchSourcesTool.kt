package com.briefy.api.infrastructure.mcp.tools

import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceEmbeddingRepository
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkRepository
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkStatus
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkTargetType
import com.briefy.api.infrastructure.ai.EmbeddingAdapter
import com.briefy.api.infrastructure.mcp.CurrentMcpUser
import com.briefy.api.infrastructure.mcp.McpJson
import org.springframework.ai.tool.ToolCallback
import org.springframework.ai.tool.function.FunctionToolCallback
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.function.Function

@Component
class SearchSourcesTool(
    private val embeddingAdapter: EmbeddingAdapter,
    private val sourceEmbeddingRepository: SourceEmbeddingRepository,
    private val sourceRepository: SourceRepository,
    private val topicLinkRepository: TopicLinkRepository,
    private val mcpJson: McpJson,
) {
    data class Input(val query: String, val topicId: String? = null, val limit: Int? = null)

    data class Item(
        val id: UUID,
        val title: String?,
        val author: String?,
        val platform: String?,
        val url: String,
        val excerpt: String?,
        val publishedDate: Instant?,
        val topics: List<String>,
        val score: Double,
    )

    fun toolCallback(): ToolCallback = FunctionToolCallback.builder(
        "search_sources",
        Function<Input, String> { execute(it) }
    )
        .description("Semantic search across the user's ingested sources. Returns the most relevant sources for a natural-language query, with title, URL, author, platform, an excerpt, publishedDate, and assigned topic names.")
        .inputType(Input::class.java)
        .build()

    private fun execute(input: Input): String {
        val userId = CurrentMcpUser.userId()
        val limit = (input.limit ?: 5).coerceIn(1, 20)
        val topicId = input.topicId?.takeIf { it.isNotBlank() }?.let { runCatching { UUID.fromString(it) }.getOrNull() }

        val query = input.query.trim()
        if (query.isEmpty()) return mcpJson.stringify(emptyList<Item>())

        val embedding = embeddingAdapter.embed(query)
        val fetchLimit = if (topicId != null) (limit * 4).coerceAtMost(80) else limit
        val matches = sourceEmbeddingRepository.findSimilar(userId, embedding, fetchLimit)

        if (matches.isEmpty()) return mcpJson.stringify(emptyList<Item>())

        val candidateIds = matches.map { it.sourceId }
        val filteredIds: List<UUID> = if (topicId != null) {
            val allowed = topicLinkRepository.findByUserIdAndTopicIdAndTargetTypeAndStatusOrderByAssignedAtDesc(
                userId, topicId, TopicLinkTargetType.SOURCE, TopicLinkStatus.ACTIVE
            ).mapTo(mutableSetOf()) { it.targetId }
            candidateIds.filter { it in allowed }
        } else {
            candidateIds
        }.take(limit)

        if (filteredIds.isEmpty()) return mcpJson.stringify(emptyList<Item>())

        val sourcesById = sourceRepository.findAllByUserIdAndIdIn(userId, filteredIds).associateBy { it.id }
        val topicNamesBySource = topicLinkRepository.findActiveTopicsBySourceIds(userId, filteredIds)
            .groupBy({ it.sourceId }, { it.topicName })

        val scoreById = matches.associate { it.sourceId to it.score }

        val items = filteredIds.mapNotNull { id ->
            val s = sourcesById[id] ?: return@mapNotNull null
            Item(
                id = s.id,
                title = s.metadata?.title,
                author = s.metadata?.author,
                platform = s.url.platform,
                url = s.url.raw,
                excerpt = mcpJson.excerpt(s.content?.text, 300),
                publishedDate = s.metadata?.publishedDate,
                topics = topicNamesBySource[s.id].orEmpty(),
                score = scoreById[s.id] ?: 0.0,
            )
        }

        return mcpJson.stringify(items)
    }
}
