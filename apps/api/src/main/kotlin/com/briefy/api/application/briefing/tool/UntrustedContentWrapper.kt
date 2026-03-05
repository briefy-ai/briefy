package com.briefy.api.application.briefing.tool

object UntrustedContentWrapper {

    private const val BOUNDARY_OPEN = "▶UNTRUSTED_EXTERNAL_CONTENT▶"
    private const val BOUNDARY_CLOSE = "◀UNTRUSTED_EXTERNAL_CONTENT◀"

    fun wrap(content: String, sourceUrl: String): String {
        val sanitized = sanitizeMarkers(content)
        return buildString {
            appendLine(BOUNDARY_OPEN)
            appendLine("[source: $sourceUrl]")
            appendLine(sanitized)
            append(BOUNDARY_CLOSE)
        }
    }

    fun wrapSearchResults(results: List<WebSearchResult>, query: String): String {
        return buildString {
            appendLine(BOUNDARY_OPEN)
            appendLine("[web_search query: $query]")
            results.forEachIndexed { i, r ->
                appendLine("${i + 1}. ${sanitizeMarkers(r.title)}")
                appendLine("   url: ${r.url}")
                appendLine("   ${sanitizeMarkers(r.snippet)}")
            }
            append(BOUNDARY_CLOSE)
        }
    }

    fun sanitizeMarkers(text: String): String {
        return text
            .replace(BOUNDARY_OPEN, "[boundary-removed]")
            .replace(BOUNDARY_CLOSE, "[boundary-removed]")
            .replace("▶UNTRUSTED", "[boundary-removed]")
            .replace("UNTRUSTED_EXTERNAL_CONTENT▶", "[boundary-removed]")
            .replace("◀UNTRUSTED", "[boundary-removed]")
            .replace("UNTRUSTED_EXTERNAL_CONTENT◀", "[boundary-removed]")
    }
}
