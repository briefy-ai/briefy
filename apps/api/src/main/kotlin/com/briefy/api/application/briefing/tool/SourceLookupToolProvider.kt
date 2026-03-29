package com.briefy.api.application.briefing.tool

import com.briefy.api.application.enrichment.SimilarSourceResult
import com.briefy.api.application.enrichment.SourceSimilarityService
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import java.util.UUID

class SourceLookupToolProvider(
    private val sourceSimilarityService: SourceSimilarityService,
    private val sourceRepository: SourceRepository
) : SourceLookupTool {

    override fun lookup(
        query: String?,
        sourceId: UUID?,
        limit: Int,
        userId: UUID,
        excludeSourceIds: Set<UUID>
    ): ToolResult<SourceLookupResponse> {
        val normalizedLimit = limit.coerceIn(1, MAX_LIMIT)
        val trimmedQuery = query?.trim()?.takeIf { it.isNotBlank() }

        val response = when {
            trimmedQuery != null -> SourceLookupResponse(
                results = hydrateResults(
                    userId = userId,
                    similarSources = sourceSimilarityService.findSimilarSources(
                        userId = userId,
                        query = trimmedQuery,
                        limit = normalizedLimit,
                        excludeSourceIds = excludeSourceIds
                    )
                ),
                mode = "query",
                query = trimmedQuery,
                sourceId = null
            )

            sourceId != null -> SourceLookupResponse(
                results = hydrateResults(
                    userId = userId,
                    similarSources = sourceSimilarityService.findSimilarSourcesBySourceId(
                        userId = userId,
                        sourceId = sourceId,
                        limit = normalizedLimit,
                        excludeSourceIds = excludeSourceIds
                    )
                ),
                mode = "source",
                query = null,
                sourceId = sourceId
            )

            else -> return ToolResult.Error(ToolErrorCode.PARSE_ERROR, "source_lookup requires either 'query' or 'sourceId'")
        }

        return ToolResult.Success(response)
    }

    private fun hydrateResults(userId: UUID, similarSources: List<SimilarSourceResult>): List<SourceLookupResult> {
        if (similarSources.isEmpty()) {
            return emptyList()
        }

        val sourcesById = sourceRepository.findAllByUserIdAndIdIn(userId, similarSources.map { it.sourceId })
            .associateBy { it.id }

        return similarSources.map { result ->
            SourceLookupResult(
                sourceId = result.sourceId,
                title = result.title,
                url = result.url,
                score = result.score,
                wordCount = result.wordCount,
                excerpt = sourcesById[result.sourceId]
                    ?.content
                    ?.text
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::truncateExcerpt)
            )
        }
    }

    private fun truncateExcerpt(text: String): String {
        return if (text.length > MAX_EXCERPT_CHARS) text.take(MAX_EXCERPT_CHARS) + "..." else text
    }

    companion object {
        private const val MAX_LIMIT = 10
        private const val MAX_EXCERPT_CHARS = 1_000
    }
}
