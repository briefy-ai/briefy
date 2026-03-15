package com.briefy.api.infrastructure.tts

import org.jsoup.Jsoup
import org.springframework.stereotype.Component

@Component
class MarkdownStripper {
    fun strip(markdown: String): String {
        if (markdown.isBlank()) return ""

        val withoutCodeBlocks = markdown.replace(FENCED_CODE_BLOCK_REGEX, " ")
        val normalized = withoutCodeBlocks
            .replace(IMAGE_REGEX, "$1")
            .replace(LINK_REGEX, "$1")
            .replace(INLINE_CODE_REGEX, "$1")
            .replace(LINE_MARKER_REGEX, "")
            .replace(EMPHASIS_REGEX, "$2")
            .replace(TABLE_SEPARATOR_REGEX, " ")
            .replace(BRACKETED_REFERENCE_REGEX, " ")

        return Jsoup.parse(normalized).text()
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    companion object {
        private val FENCED_CODE_BLOCK_REGEX = Regex("(?s)```.*?```")
        private val IMAGE_REGEX = Regex("!\\[([^]]*)]\\([^)]*\\)")
        private val LINK_REGEX = Regex("\\[([^]]+)]\\([^)]*\\)")
        private val INLINE_CODE_REGEX = Regex("`([^`]*)`")
        private val LINE_MARKER_REGEX = Regex("(?m)^\\s{0,3}(#{1,6}|>|[-*+]|\\d+\\.)\\s+")
        private val EMPHASIS_REGEX = Regex("(\\*\\*|__|~~|\\*|_)(.*?)\\1")
        private val TABLE_SEPARATOR_REGEX = Regex("[|]")
        private val BRACKETED_REFERENCE_REGEX = Regex("\\[[^]]*]:\\s*\\S+")
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}
