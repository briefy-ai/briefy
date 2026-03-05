package com.briefy.api.application.briefing.tool

sealed interface ToolResult<out T> {
    data class Success<T>(val data: T) : ToolResult<T>
    data class Error(val code: ToolErrorCode, val message: String) : ToolResult<Nothing>
}

enum class ToolErrorCode(val retryable: Boolean) {
    TIMEOUT(true),
    HTTP_429(true),
    HTTP_5XX(true),
    NETWORK_ERROR(true),

    SSRF_BLOCKED(false),
    CONTENT_TOO_LARGE(false),
    INVALID_URL(false),
    PROVIDER_AUTH_ERROR(false),
    PARSE_ERROR(false),
    UNKNOWN(false);

    fun toRunnerErrorCode(): String = name.lowercase()
}
