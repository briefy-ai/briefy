package com.briefy.api.domain.knowledgegraph.briefing

import java.time.Instant
import java.util.UUID

interface BriefingSearchRepository {
    fun searchReady(
        userId: UUID,
        query: String,
        topicId: UUID?,
        limit: Int
    ): List<BriefingSearchHit>
}

data class BriefingSearchHit(
    val id: UUID,
    val title: String?,
    val contentMarkdown: String?,
    val createdAt: Instant
)
