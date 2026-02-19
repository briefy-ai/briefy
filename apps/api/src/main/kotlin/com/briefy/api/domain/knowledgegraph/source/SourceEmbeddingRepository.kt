package com.briefy.api.domain.knowledgegraph.source

import java.time.Instant
import java.util.UUID

interface SourceEmbeddingRepository {
    fun upsert(sourceId: UUID, userId: UUID, embedding: List<Double>, now: Instant)

    fun findSimilar(
        userId: UUID,
        queryEmbedding: List<Double>,
        limit: Int,
        excludeSourceId: UUID? = null
    ): List<SourceSimilarityMatch>
}
