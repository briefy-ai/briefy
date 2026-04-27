package com.briefy.api.infrastructure.mcp.tools

import com.briefy.api.domain.knowledgegraph.source.SourceEmbeddingRepository
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkRepository
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
        val rawTopicId = input.topicId?.trim()?.takeIf { it.isNotEmpty() }
        val topicId = rawTopicId?.let {
            runCatching { UUID.fromString(it) }.getOrElse {
                return mcpJson.stringify(mapOf("error" to "Invalid topicId"))
            }
        }

        val query = input.query.trim()
        if (query.isEmpty()) return mcpJson.stringify(emptyList<Item>())

        val embedding = embeddingAdapter.embed(query)

        val matches = if (topicId != null) {
            val allowedIds = topicLinkRepository.findActiveSourceIdsByTopic(userId, topicId)
            if (allowedIds.isEmpty()) return mcpJson.stringify(emptyList<Item>())
            sourceEmbeddingRepository.findSimilarRestrictedToSources(userId, embedding, allowedIds, limit)
        } else {
            sourceEmbeddingRepository.findSimilar(userId, embedding, limit)
        }

        if (matches.isEmpty()) return mcpJson.stringify(emptyList<Item>())

        val ids = matches.map { it.sourceId }
        val sourcesById = sourceRepository.findAllByUserIdAndIdIn(userId, ids).associateBy { it.id }
        val topicNamesBySource = topicLinkRepository.findActiveTopicsBySourceIds(userId, ids)
            .groupBy({ it.sourceId }, { it.topicName })

        val items = matches.mapNotNull { match ->
            val s = sourcesById[match.sourceId] ?: return@mapNotNull null
            Item(
                id = s.id,
                title = s.metadata?.title,
                author = s.metadata?.author,
                platform = s.url.platform,
                url = s.url.raw,
                excerpt = mcpJson.excerpt(s.content?.text, 300),
                publishedDate = s.metadata?.publishedDate,
                topics = topicNamesBySource[s.id].orEmpty(),
                score = match.score,
            )
        }

        return mcpJson.stringify(items)
    }
}
