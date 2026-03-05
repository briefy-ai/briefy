package com.briefy.api.application.briefing.tool

interface WebSearchTool {
    fun search(query: String, maxResults: Int = 5): ToolResult<WebSearchResponse>
}

data class WebSearchResponse(
    val results: List<WebSearchResult>,
    val query: String
)

data class WebSearchResult(
    val title: String,
    val url: String,
    val snippet: String
)
