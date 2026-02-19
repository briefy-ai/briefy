package com.briefy.api.application.enrichment

import com.briefy.api.domain.knowledgegraph.source.SourceEmbeddingRepository
import com.briefy.api.infrastructure.ai.EmbeddingAdapter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class SourceSimilarityService(
    private val sourceEmbeddingRepository: SourceEmbeddingRepository,
    private val embeddingAdapter: EmbeddingAdapter
) {
    private val logger = LoggerFactory.getLogger(SourceSimilarityService::class.java)

    @Transactional(readOnly = true)
    fun findSimilarSources(
        userId: UUID,
        query: String,
        limit: Int,
        excludeSourceId: UUID? = null
    ): List<SimilarSourceResult> {
        if (query.isBlank() || limit <= 0) {
            return emptyList()
        }
        val normalizedLimit = limit.coerceAtMost(MAX_LIMIT)

        val queryEmbedding = try {
            embeddingAdapter.embed(query)
        } catch (e: Exception) {
            logger.warn("[similarity] skipped userId={} reason=query_embedding_failed", userId, e)
            return emptyList()
        }

        return sourceEmbeddingRepository.findSimilar(
            userId = userId,
            queryEmbedding = queryEmbedding,
            limit = normalizedLimit,
            excludeSourceId = excludeSourceId
        ).map {
            SimilarSourceResult(
                sourceId = it.sourceId,
                score = it.score,
                title = it.title,
                url = it.urlNormalized,
                wordCount = it.wordCount
            )
        }
    }

    companion object {
        private const val MAX_LIMIT = 50
    }
}


data class SimilarSourceResult(
    val sourceId: UUID,
    val score: Double,
    val title: String?,
    val url: String,
    val wordCount: Int
)
