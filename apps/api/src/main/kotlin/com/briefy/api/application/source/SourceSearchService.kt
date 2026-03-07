package com.briefy.api.application.source

import com.briefy.api.application.enrichment.SourceSimilarityService
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import org.springframework.stereotype.Service

@Service
class SourceSearchService(
    private val sourceSimilarityService: SourceSimilarityService,
    private val sourceRepository: SourceRepository
) : SourceSearch {

    override fun search(request: SourceSearchRequest): List<SourceSearchHit> {
        if (request.query.isBlank() || request.limit <= 0) {
            return emptyList()
        }
        return when (request.mode) {
            SourceSearchMode.SIMILARITY -> searchBySimilarity(request)
            SourceSearchMode.TOPIC -> emptyList()
        }
    }

    private fun searchBySimilarity(request: SourceSearchRequest): List<SourceSearchHit> {
        val requestedLimit = request.limit.coerceAtMost(MAX_LIMIT)
        val lookupLimit = (requestedLimit + request.excludeSourceIds.size).coerceAtMost(MAX_LIMIT)
        val similarResults = sourceSimilarityService.findSimilarSources(
            userId = request.userId,
            query = request.query,
            limit = lookupLimit
        )
            .filterNot { request.excludeSourceIds.contains(it.sourceId) }
            .take(requestedLimit)

        if (similarResults.isEmpty()) {
            return emptyList()
        }

        val sourceById = sourceRepository.findAllByUserIdAndIdIn(
            userId = request.userId,
            ids = similarResults.map { it.sourceId }
        ).associateBy { it.id }

        return similarResults.mapNotNull { similar ->
            val source = sourceById[similar.sourceId] ?: return@mapNotNull null
            SourceSearchHit(
                sourceId = source.id,
                score = similar.score,
                title = source.metadata?.title?.takeIf(String::isNotBlank) ?: source.url.normalized,
                url = source.url.normalized,
                contentSnippet = source.content?.text
                    ?.trim()
                    ?.take(MAX_SNIPPET_CHARS)
                    ?.ifBlank { null },
                wordCount = similar.wordCount
            )
        }
    }

    companion object {
        private const val MAX_LIMIT = 50
        private const val MAX_SNIPPET_CHARS = 1200
    }
}
