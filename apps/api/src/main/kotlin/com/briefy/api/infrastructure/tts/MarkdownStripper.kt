package com.briefy.api.infrastructure.tts

import org.jsoup.Jsoup
import org.springframework.stereotype.Component

@Component
class MarkdownStripper {
    fun strip(markdown: String): String {
        if (markdown.isBlank()) return ""

        val withoutCodeBlocks = stripFencedCodeBlocks(markdown)
        val withoutLinks = replaceInlineLinks(withoutCodeBlocks)
        val normalized = withoutLinks
            .replace(INLINE_CODE_REGEX, "$1")
            .replace(LINE_MARKER_REGEX, "")
            .replace(EMPHASIS_REGEX, "$2")
            .replace(TABLE_SEPARATOR_REGEX, " ")
            .replace(BRACKETED_REFERENCE_REGEX, " ")

        return Jsoup.parse(normalized).text()
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    private fun stripFencedCodeBlocks(markdown: String): String {
        val result = StringBuilder()
        var fenceMarker: Char? = null
        var fenceLength = 0

        for (line in markdown.lineSequence()) {
            val trimmed = line.trimStart()
            if (fenceMarker == null) {
                val marker = trimmed.firstOrNull()
                val markerLength = trimmed.takeWhile { it == marker }.length
                if ((marker == '`' || marker == '~') && markerLength >= 3) {
                    fenceMarker = marker
                    fenceLength = markerLength
                    continue
                }
                result.append(line).append('\n')
                continue
            }

            val markerLength = trimmed.takeWhile { it == fenceMarker }.length
            if (markerLength >= fenceLength && trimmed.drop(markerLength).isBlank()) {
                fenceMarker = null
                fenceLength = 0
            }
        }

        return result.toString()
    }

    private fun replaceInlineLinks(markdown: String): String {
        val result = StringBuilder()
        var index = 0

        while (index < markdown.length) {
            val replacement = parseInlineTarget(markdown, index)
            if (replacement != null) {
                result.append(replacement.text)
                index = replacement.nextIndex
                continue
            }
            result.append(markdown[index])
            index++
        }

        return result.toString()
    }

    private fun parseInlineTarget(markdown: String, startIndex: Int): InlineTargetReplacement? {
        val labelStart = when {
            markdown.startsWith("![", startIndex) -> startIndex + 2
            markdown[startIndex] == '[' -> startIndex + 1
            else -> return null
        }

        val labelEnd = markdown.indexOf(']', labelStart)
        if (labelEnd == -1 || labelEnd + 1 >= markdown.length || markdown[labelEnd + 1] != '(') {
            return null
        }

        var cursor = labelEnd + 2
        var depth = 1
        while (cursor < markdown.length) {
            when (markdown[cursor]) {
                '(' -> depth++
                ')' -> depth--
            }
            if (depth == 0) {
                return InlineTargetReplacement(
                    text = markdown.substring(labelStart, labelEnd),
                    nextIndex = cursor + 1
                )
            }
            cursor++
        }

        return null
    }

    companion object {
        private val INLINE_CODE_REGEX = Regex("`([^`]*)`")
        private val LINE_MARKER_REGEX = Regex("(?m)^\\s{0,3}(#{1,6}|>|[-*+]|\\d+\\.)\\s+")
        private val EMPHASIS_REGEX = Regex("(\\*\\*|__|~~|\\*|_)(.*?)\\1")
        private val TABLE_SEPARATOR_REGEX = Regex("[|]")
        private val BRACKETED_REFERENCE_REGEX = Regex("\\[[^]]*]:\\s*\\S+")
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}

private data class InlineTargetReplacement(
    val text: String,
    val nextIndex: Int
)
