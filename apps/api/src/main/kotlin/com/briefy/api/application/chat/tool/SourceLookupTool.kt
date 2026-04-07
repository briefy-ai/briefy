package com.briefy.api.application.chat.tool

import java.time.Instant
import java.util.UUID

interface SourceLookupTool {
    fun lookup(request: SourceLookupRequest): SourceLookupResult
}

data class SourceLookupRequest(
    val sourceId: UUID? = null,
    val query: String? = null,
    val filter: String? = null,
    val sourceType: String? = null,
    val topicId: UUID? = null,
    val includeContent: Boolean = false,
    val findSimilar: Boolean = false,
    val limit: Int? = null
)

sealed interface SourceLookupResult

data class SourceList(
    val sources: List<SourceListItem>,
    val truncated: Boolean = false,
    val hint: String? = null
) : SourceLookupResult

data class SourceDetail(
    val id: UUID,
    val title: String?,
    val author: String?,
    val url: String,
    val sourceType: String,
    val wordCount: Int,
    val isRead: Boolean,
    val publishedDate: Instant?,
    val platform: String?,
    val topics: List<SourceTopicItem>
) : SourceLookupResult

data class SourceContent(
    val id: UUID,
    val title: String?,
    val content: String,
    val truncated: Boolean,
    val wordCount: Int
) : SourceLookupResult

data class SourceSearchResults(
    val results: List<SourceSearchMatch>,
    val hint: String? = null
) : SourceLookupResult

data class SourceLookupError(
    val message: String
) : SourceLookupResult

data class SourceListItem(
    val id: UUID,
    val title: String?,
    val url: String,
    val type: String,
    val wordCount: Int,
    val isRead: Boolean,
    val createdAt: Instant
)

data class SourceTopicItem(
    val id: UUID,
    val name: String
)

data class SourceSearchMatch(
    val id: UUID,
    val title: String?,
    val url: String,
    val wordCount: Int,
    val score: Double
)
