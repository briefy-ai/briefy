package com.briefy.api.application.briefing.tool

interface WebFetchTool {
    fun fetch(url: String): ToolResult<WebFetchResponse>
}

data class WebFetchResponse(
    val url: String,
    val title: String?,
    val content: String,
    val contentLengthBytes: Int
)
