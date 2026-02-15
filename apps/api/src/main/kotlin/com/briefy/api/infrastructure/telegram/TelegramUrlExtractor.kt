package com.briefy.api.infrastructure.telegram

import org.springframework.stereotype.Component

@Component
class TelegramUrlExtractor {
    fun extract(text: String, maxUrls: Int): ExtractedUrls {
        if (text.isBlank()) return ExtractedUrls(emptyList(), false, 0)
        val candidates = URL_REGEX.findAll(text)
            .map { sanitize(it.value) }
            .filter { it.isNotBlank() }
            .toList()

        if (candidates.isEmpty()) return ExtractedUrls(emptyList(), false, 0)
        val limited = candidates.take(maxUrls)
        return ExtractedUrls(
            urls = limited,
            truncated = candidates.size > maxUrls,
            skippedCount = (candidates.size - limited.size).coerceAtLeast(0)
        )
    }

    private fun sanitize(value: String): String {
        return value.trim().trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}')
    }

    companion object {
        private val URL_REGEX = Regex("""(?i)\b((?:https?://|www\.)[^\s<>"']+)""")
    }
}

data class ExtractedUrls(
    val urls: List<String>,
    val truncated: Boolean,
    val skippedCount: Int
)
