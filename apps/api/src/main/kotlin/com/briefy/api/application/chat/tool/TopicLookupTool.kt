package com.briefy.api.application.chat.tool

import java.util.UUID

interface TopicLookupTool {
    fun lookup(request: TopicLookupRequest): TopicLookupResult
}

data class TopicLookupRequest(
    val topicId: UUID? = null,
    val filter: String? = null,
    val includeSourceIds: Boolean = false,
    val status: String? = null
)

sealed interface TopicLookupResult

data class TopicList(
    val topics: List<TopicListItem>,
    val truncated: Boolean = false,
    val hint: String? = null
) : TopicLookupResult

data class TopicDetail(
    val id: UUID,
    val name: String,
    val status: String,
    val sources: List<TopicSourceDetail>
) : TopicLookupResult

data class TopicLookupError(
    val message: String
) : TopicLookupResult

data class TopicListItem(
    val id: UUID,
    val name: String,
    val status: String,
    val sourceCount: Long,
    val sourceIds: List<UUID>? = null
)

data class TopicSourceDetail(
    val id: UUID,
    val title: String?,
    val url: String,
    val sourceType: String,
    val isRead: Boolean,
    val wordCount: Int
)
