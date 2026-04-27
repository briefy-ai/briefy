package com.briefy.api.infrastructure.mcp.tools

import com.briefy.api.domain.knowledgegraph.briefing.BriefingReferenceRepository
import com.briefy.api.domain.knowledgegraph.briefing.BriefingReferenceStatus
import com.briefy.api.domain.knowledgegraph.briefing.BriefingSearchRepository
import com.briefy.api.domain.knowledgegraph.briefing.BriefingSourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceEmbeddingRepository
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.topic.TopicRepository
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
class SearchRelatedTool(
    private val embeddingAdapter: EmbeddingAdapter,
    private val sourceEmbeddingRepository: SourceEmbeddingRepository,
    private val sourceRepository: SourceRepository,
    private val briefingSearchRepository: BriefingSearchRepository,
    private val briefingSourceRepository: BriefingSourceRepository,
    private val briefingReferenceRepository: BriefingReferenceRepository,
    private val topicLinkRepository: TopicLinkRepository,
    private val topicRepository: TopicRepository,
    private val mcpJson: McpJson,
) {
    data class Input(val query: String, val types: List<String>? = null, val limit: Int? = null)

    data class SourceItem(
        val id: UUID,
        val title: String?,
        val author: String?,
        val platform: String?,
        val url: String,
        val excerpt: String?,
        val publishedDate: Instant?,
        val topics: List<String>,
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

    data class Hit(val type: String, val score: Double, val item: Any)

    fun toolCallback(): ToolCallback = FunctionToolCallback.builder(
        "search_related",
        Function<Input, String> { execute(it) }
    )
        .description("Cross-entity semantic search. Returns a single ranked list mixing sources (vector similarity) and briefings (substring match) most relevant to the query. Each result has {type, score, item}. The 'types' field defaults to ['source','briefing']; 'takeaway' is accepted but yields no results until takeaways are implemented.")
        .inputType(Input::class.java)
        .build()

    private fun execute(input: Input): String {
        val userId = CurrentMcpUser.userId()
        val limit = (input.limit ?: 10).coerceIn(1, 30)
        val types = (input.types ?: listOf("source", "briefing")).map { it.lowercase() }.toSet()
        val query = input.query.trim()
        if (query.isEmpty()) return mcpJson.stringify(emptyList<Hit>())

        val hits = mutableListOf<Hit>()

        if ("source" in types) {
            hits += sourceHits(userId, query, limit * 2)
        }
        if ("briefing" in types) {
            hits += briefingHits(userId, query, limit * 2)
        }

        val ranked = hits.sortedByDescending { it.score }.take(limit)
        return mcpJson.stringify(ranked)
    }

    private fun sourceHits(userId: UUID, query: String, fetchLimit: Int): List<Hit> {
        val embedding = embeddingAdapter.embed(query)
        val matches = sourceEmbeddingRepository.findSimilar(userId, embedding, fetchLimit)
        if (matches.isEmpty()) return emptyList()
        val ids = matches.map { it.sourceId }
        val byId = sourceRepository.findAllByUserIdAndIdIn(userId, ids).associateBy { it.id }
        val topicNamesBySource = topicLinkRepository.findActiveTopicsBySourceIds(userId, ids)
            .groupBy({ it.sourceId }, { it.topicName })
        return matches.mapNotNull { match ->
            val s = byId[match.sourceId] ?: return@mapNotNull null
            Hit(
                type = "source",
                score = match.score,
                item = SourceItem(
                    id = s.id,
                    title = s.metadata?.title,
                    author = s.metadata?.author,
                    platform = s.url.platform,
                    url = s.url.raw,
                    excerpt = mcpJson.excerpt(s.content?.text, 300),
                    publishedDate = s.metadata?.publishedDate,
                    topics = topicNamesBySource[s.id].orEmpty(),
                )
            )
        }
    }

    private fun briefingHits(userId: UUID, query: String, fetchLimit: Int): List<Hit> {
        val results = briefingSearchRepository.searchReady(userId, query, null, fetchLimit)
        if (results.isEmpty()) return emptyList()
        return results.mapIndexed { index, hit ->
            val sourceIds = briefingSourceRepository.findByBriefingIdOrderByCreatedAtAsc(hit.id).map { it.sourceId }
            val refs = briefingReferenceRepository.findByBriefingIdOrderByCreatedAtAsc(hit.id)
                .filter { it.status == BriefingReferenceStatus.ACTIVE }
                .map { Reference(url = it.url, title = it.title, snippet = it.snippet) }
            val score = 0.7 - (index * 0.05).coerceAtMost(0.5)
            Hit(
                type = "briefing",
                score = score,
                item = BriefingItem(
                    id = hit.id,
                    title = hit.title,
                    synthesizedText = mcpJson.excerpt(hit.contentMarkdown, 500),
                    sourceIds = sourceIds,
                    references = refs,
                    createdAt = hit.createdAt,
                )
            )
        }
    }
}
