package com.briefy.api.application.chat.tool

import com.briefy.api.application.enrichment.SimilarSourceResult
import com.briefy.api.application.enrichment.SourceSimilarityService
import com.briefy.api.application.source.SourceSortStrategy
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.domain.knowledgegraph.source.SourceType
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkRepository
import com.briefy.api.infrastructure.security.CurrentUserProvider
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class SourceLookupToolProvider(
    private val sourceRepository: SourceRepository,
    private val sourceSimilarityService: SourceSimilarityService,
    private val topicLinkRepository: TopicLinkRepository,
    private val currentUserProvider: CurrentUserProvider
) : SourceLookupTool {

    @Transactional(readOnly = true)
    override fun lookup(request: SourceLookupRequest): SourceLookupResult {
        val userId = currentUserProvider.requireUserId()
        val normalizedLimit = normalizeLimit(request.limit)
        val trimmedQuery = request.query?.trim()?.takeIf { it.isNotBlank() }

        return when {
            trimmedQuery != null -> lookupSemanticSearch(userId, trimmedQuery, normalizedLimit)
            request.findSimilar -> lookupSimilarSources(userId, request.sourceId, normalizedLimit)
            request.includeContent -> lookupSourceContent(userId, request.sourceId)
            request.sourceId != null -> lookupSourceDetail(userId, request.sourceId)
            else -> lookupSourceList(userId, request, normalizedLimit)
        }
    }

    private fun lookupSourceList(
        userId: UUID,
        request: SourceLookupRequest,
        limit: Int
    ): SourceLookupResult {
        val filter = request.filter?.trim()?.takeIf { it.isNotBlank() }
        val rawSourceType = request.sourceType?.trim()?.takeIf { it.isNotBlank() }
        val sourceType = if (rawSourceType == null) {
            null
        } else {
            parseSourceType(rawSourceType) ?: return invalidSourceTypeError()
        }

        return if (filter != null) {
            lookupFilteredSourceList(
                userId = userId,
                filter = filter,
                sourceType = sourceType,
                topicId = request.topicId,
                limit = limit
            )
        } else {
            lookupPagedSourceList(
                userId = userId,
                sourceType = sourceType,
                topicId = request.topicId,
                limit = limit
            )
        }
    }

    private fun lookupPagedSourceList(
        userId: UUID,
        sourceType: SourceType?,
        topicId: UUID?,
        limit: Int
    ): SourceLookupResult {
        val sources = sourceRepository.findSourcesPage(
            userId = userId,
            status = SourceStatus.ACTIVE,
            topicIds = topicId?.let(::listOf),
            sourceType = sourceType,
            sort = SourceSortStrategy.NEWEST,
            cursor = null,
            limit = limit
        )
        val truncated = sources.size > limit
        val limitedSources = sources.take(limit)

        return SourceList(
            sources = limitedSources.map(::toSourceListItem),
            truncated = truncated,
            hint = truncatedHint(truncated, limit)
        )
    }

    private fun lookupFilteredSourceList(
        userId: UUID,
        filter: String,
        sourceType: SourceType?,
        topicId: UUID?,
        limit: Int
    ): SourceLookupResult {
        val rankedMatches = sourceRepository.searchSources(userId, filter, limit + 1)
        val truncated = rankedMatches.size > limit
        val hydratedSourcesById = sourceRepository.findAllByUserIdAndIdIn(
            userId,
            rankedMatches.map { it.id }
        ).associateBy { it.id }
        val activeTopicIdsBySourceId = loadActiveTopicIdsBySourceId(userId, hydratedSourcesById.keys.toList())

        val sources = rankedMatches
            .mapNotNull { projection -> hydratedSourcesById[projection.id] }
            .filter { source -> matchesFilters(source, sourceType, topicId, activeTopicIdsBySourceId) }
            .take(limit)

        return SourceList(
            sources = sources.map(::toSourceListItem),
            truncated = truncated,
            hint = truncatedHint(truncated, limit)
        )
    }

    private fun lookupSourceDetail(userId: UUID, sourceId: UUID): SourceLookupResult {
        val source = sourceRepository.findByIdAndUserId(sourceId, userId)
            ?: return SourceLookupError("Source not found for source_lookup.")
        val topics = loadActiveTopicsBySourceId(userId, listOf(sourceId))[sourceId].orEmpty()

        return SourceDetail(
            id = source.id,
            title = source.metadata?.title,
            author = source.metadata?.author,
            url = source.url.normalized,
            sourceType = source.sourceType.name.lowercase(),
            wordCount = source.content?.wordCount ?: 0,
            isRead = source.isRead,
            publishedDate = source.metadata?.publishedDate,
            platform = source.metadata?.platform,
            topics = topics
        )
    }

    private fun lookupSourceContent(userId: UUID, sourceId: UUID?): SourceLookupResult {
        if (sourceId == null) {
            return SourceLookupError("The 'includeContent' argument for source_lookup requires 'sourceId'.")
        }

        val source = sourceRepository.findByIdAndUserId(sourceId, userId)
            ?: return SourceLookupError("Source not found for source_lookup.")
        val text = source.content?.text?.trim().orEmpty()
        if (text.isBlank()) {
            return SourceLookupError("Source content not available for source_lookup.")
        }

        val truncated = truncateContent(text)
        return SourceContent(
            id = source.id,
            title = source.metadata?.title,
            content = truncated.content,
            truncated = truncated.truncated,
            wordCount = source.content?.wordCount ?: 0
        )
    }

    private fun lookupSemanticSearch(userId: UUID, query: String, limit: Int): SourceLookupResult {
        return SourceSearchResults(
            results = sourceSimilarityService.findSimilarSources(
                userId = userId,
                query = query,
                limit = limit
            ).map(::toSourceSearchMatch)
        )
    }

    private fun lookupSimilarSources(
        userId: UUID,
        sourceId: UUID?,
        limit: Int
    ): SourceLookupResult {
        if (sourceId == null) {
            return SourceLookupError("The 'findSimilar' argument for source_lookup requires 'sourceId'.")
        }

        val source = sourceRepository.findByIdAndUserId(sourceId, userId)
            ?: return SourceLookupError("Source not found for source_lookup.")

        return SourceSearchResults(
            results = sourceSimilarityService.findSimilarSourcesBySourceId(
                userId = userId,
                sourceId = source.id,
                limit = limit,
                excludeSourceIds = setOf(source.id)
            ).map(::toSourceSearchMatch)
        )
    }

    private fun loadActiveTopicsBySourceId(
        userId: UUID,
        sourceIds: List<UUID>
    ): Map<UUID, List<SourceTopicItem>> {
        if (sourceIds.isEmpty()) {
            return emptyMap()
        }

        return topicLinkRepository.findActiveTopicsBySourceIds(userId, sourceIds)
            .groupBy { it.sourceId }
            .mapValues { (_, topics) ->
                topics.map { topic ->
                    SourceTopicItem(
                        id = topic.topicId,
                        name = topic.topicName
                    )
                }
            }
    }

    private fun loadActiveTopicIdsBySourceId(
        userId: UUID,
        sourceIds: List<UUID>
    ): Map<UUID, Set<UUID>> {
        if (sourceIds.isEmpty()) {
            return emptyMap()
        }

        return topicLinkRepository.findActiveTopicsBySourceIds(userId, sourceIds)
            .groupBy { it.sourceId }
            .mapValues { (_, topics) -> topics.map { it.topicId }.toSet() }
    }

    private fun matchesFilters(
        source: Source,
        sourceType: SourceType?,
        topicId: UUID?,
        activeTopicIdsBySourceId: Map<UUID, Set<UUID>>
    ): Boolean {
        if (sourceType != null && source.sourceType != sourceType) {
            return false
        }
        if (topicId != null && topicId !in activeTopicIdsBySourceId[source.id].orEmpty()) {
            return false
        }
        return true
    }

    private fun truncateContent(text: String): TruncatedContent {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size <= MAX_CONTENT_WORDS) {
            return TruncatedContent(text, false)
        }

        return TruncatedContent(
            content = words.take(MAX_CONTENT_WORDS).joinToString(" "),
            truncated = true
        )
    }

    private fun toSourceListItem(source: Source): SourceListItem {
        return SourceListItem(
            id = source.id,
            title = source.metadata?.title,
            url = source.url.normalized,
            type = source.sourceType.name.lowercase(),
            wordCount = source.content?.wordCount ?: 0,
            isRead = source.isRead,
            createdAt = source.createdAt
        )
    }

    private fun toSourceSearchMatch(result: SimilarSourceResult): SourceSearchMatch {
        return SourceSearchMatch(
            id = result.sourceId,
            title = result.title,
            url = result.url,
            wordCount = result.wordCount,
            score = result.score
        )
    }

    private fun parseSourceType(sourceType: String): SourceType? {
        return runCatching { SourceType.valueOf(sourceType.uppercase()) }.getOrNull()
    }

    private fun invalidSourceTypeError(): SourceLookupError {
        return SourceLookupError(
            "Invalid 'sourceType' argument for source_lookup. Expected NEWS, BLOG, RESEARCH, or VIDEO."
        )
    }

    private fun truncatedHint(truncated: Boolean, limit: Int): String? {
        if (!truncated) {
            return null
        }

        return "Results truncated to $limit sources. Narrow the search with 'filter', 'sourceType', or 'topicId'."
    }

    private fun normalizeLimit(limit: Int?): Int {
        return (limit ?: DEFAULT_LIMIT).coerceIn(1, MAX_SOURCES)
    }

    private data class TruncatedContent(
        val content: String,
        val truncated: Boolean
    )

    companion object {
        private const val MAX_SOURCES = 50
        private const val DEFAULT_LIMIT = 20
        private const val MAX_CONTENT_WORDS = 3_000
    }
}
