package com.briefy.api.domain.knowledgegraph.source

import com.briefy.api.application.source.SourceListCursor
import com.briefy.api.application.source.SourceSortStrategy
import java.util.UUID

interface SourceRepositoryCustom {
    fun findSourcesPage(
        userId: UUID,
        status: SourceStatus,
        topicIds: List<UUID>?,
        sourceType: SourceType?,
        sort: SourceSortStrategy,
        cursor: SourceListCursor?,
        limit: Int
    ): List<Source>

    fun searchSources(
        userId: UUID,
        query: String,
        limit: Int
    ): List<SourceSearchProjection>
}

data class SourceSearchProjection(
    val id: UUID,
    val title: String?,
    val author: String?,
    val domain: String?,
    val sourceType: SourceType
)
