package com.briefy.api.application.source

import java.util.UUID

interface SourceSearch {
    fun search(request: SourceSearchRequest): List<SourceSearchHit>
}

data class SourceSearchRequest(
    val userId: UUID,
    val query: String,
    val mode: SourceSearchMode = SourceSearchMode.SIMILARITY,
    val limit: Int = 5,
    val excludeSourceIds: Set<UUID> = emptySet()
)

enum class SourceSearchMode(val value: String) {
    SIMILARITY("similarity"),
    TOPIC("topic");

    companion object {
        fun fromRaw(raw: String?): SourceSearchMode? {
            if (raw.isNullOrBlank()) {
                return SIMILARITY
            }
            return entries.firstOrNull { it.value.equals(raw.trim(), ignoreCase = true) }
        }
    }
}

data class SourceSearchHit(
    val sourceId: UUID,
    val score: Double,
    val title: String,
    val url: String,
    val contentSnippet: String?,
    val wordCount: Int
)
