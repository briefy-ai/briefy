package com.briefy.api.application.briefing.tool

import java.util.UUID

interface SourceLookupTool {
    fun lookup(
        query: String?,
        sourceId: UUID?,
        limit: Int = 5,
        userId: UUID,
        excludeSourceIds: Set<UUID> = emptySet()
    ): ToolResult<SourceLookupResponse>
}

data class SourceLookupResponse(
    val results: List<SourceLookupResult>,
    val mode: String,
    val query: String?,
    val sourceId: UUID?
)

data class SourceLookupResult(
    val sourceId: UUID,
    val title: String?,
    val url: String,
    val score: Double,
    val wordCount: Int,
    val excerpt: String?
)
