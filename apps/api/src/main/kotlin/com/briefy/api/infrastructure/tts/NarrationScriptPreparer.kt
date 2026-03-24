package com.briefy.api.infrastructure.tts

import org.springframework.stereotype.Component

@Component
class NarrationScriptPreparer(
    private val markdownStripper: MarkdownStripper
) {
    fun prepare(content: String): String {
        if (content.isBlank()) return ""

        val parts = mutableListOf<String>()
        var index = 0
        val lines = content.lines()

        while (index < lines.size) {
            if (lines[index].isBlank()) {
                index++
                continue
            }

            val fence = parseFence(lines[index])
            if (fence != null) {
                appendPart(parts, CODE_BLOCK_ANNOTATION)
                index = skipFence(lines, index + 1, fence)
                continue
            }

            val block = mutableListOf<String>()
            while (index < lines.size) {
                val line = lines[index]
                if (line.isBlank()) {
                    break
                }
                if (parseFence(line) != null) {
                    break
                }
                block += line
                index++
            }

            appendBlock(parts, block)
        }

        return parts.joinToString(" ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    private fun appendBlock(parts: MutableList<String>, lines: List<String>) {
        if (lines.isEmpty()) return

        val trimmedLines = lines.map(String::trim).filter(String::isNotBlank)
        val replacement = when {
            isMarkdownTable(trimmedLines) -> TABLE_ANNOTATION
            isDenseStructuredBlock(trimmedLines) -> DENSE_STRUCTURED_BLOCK_ANNOTATION
            else -> markdownStripper.strip(lines.joinToString("\n"))
        }

        appendPart(parts, replacement)
    }

    private fun appendPart(parts: MutableList<String>, part: String) {
        val normalized = part.trim()
        if (normalized.isBlank()) return
        if (parts.lastOrNull() == normalized) return
        parts += normalized
    }

    private fun parseFence(line: String): Fence? {
        val trimmed = line.trimStart()
        val marker = trimmed.firstOrNull() ?: return null
        if (marker != '`' && marker != '~') return null

        val markerLength = trimmed.takeWhile { it == marker }.length
        if (markerLength < 3) return null

        return Fence(marker, markerLength)
    }

    private fun skipFence(lines: List<String>, startIndex: Int, fence: Fence): Int {
        var index = startIndex
        while (index < lines.size) {
            val trimmed = lines[index].trimStart()
            val markerLength = trimmed.takeWhile { it == fence.marker }.length
            if (markerLength >= fence.length && trimmed.drop(markerLength).isBlank()) {
                return index + 1
            }
            index++
        }
        return lines.size
    }

    private fun isMarkdownTable(lines: List<String>): Boolean {
        if (lines.size < 2) return false
        val pipeLines = lines.count { TABLE_ROW_REGEX.matches(it) }
        if (pipeLines < 2) return false

        return lines.any { TABLE_SEPARATOR_REGEX.matches(it) }
    }

    private fun isDenseStructuredBlock(lines: List<String>): Boolean {
        if (lines.size < 4) return false

        val structuredLineCount = lines.count(::isStructuredLine)
        val sentenceLikeLines = lines.count(::isSentenceLikeLine)

        return structuredLineCount * 10 >= lines.size * 6 && sentenceLikeLines < 2
    }

    private fun isStructuredLine(line: String): Boolean {
        return STRUCTURED_ARROW_REGEX.containsMatchIn(line) ||
            DOCUMENT_IDENTIFIER_REGEX.containsMatchIn(line) ||
            SECTION_STEP_REGEX.matches(line) ||
            KEY_VALUE_REGEX.matches(line) ||
            shortTokenDensity(line) >= 0.6
    }

    private fun isSentenceLikeLine(line: String): Boolean {
        val words = WORD_REGEX.findAll(line).count()
        return words >= 5 && SENTENCE_PUNCTUATION_REGEX.containsMatchIn(line)
    }

    private fun shortTokenDensity(line: String): Double {
        val tokens = WORD_REGEX.findAll(line).map { it.value }.toList()
        if (tokens.size < 4) return 0.0

        val shortTokenCount = tokens.count { it.length <= 4 || DIGIT_REGEX.containsMatchIn(it) }
        return shortTokenCount.toDouble() / tokens.size.toDouble()
    }

    companion object {
        const val CODE_BLOCK_ANNOTATION = "Code example skipped for audio clarity."
        const val TABLE_ANNOTATION = "Table skipped for audio clarity."
        const val DENSE_STRUCTURED_BLOCK_ANNOTATION = "Dense structured example skipped for audio clarity."

        private val WHITESPACE_REGEX = Regex("\\s+")
        private val STRUCTURED_ARROW_REGEX = Regex("(->|→)")
        private val DOCUMENT_IDENTIFIER_REGEX = Regex("\\b[A-Z]?\\d+\\b")
        private val SECTION_STEP_REGEX = Regex("^\\d+[.)]?\\s+[A-Z][^.!?]*$")
        private val KEY_VALUE_REGEX = Regex("^[^:]{1,30}:\\s+.+$")
        private val TABLE_ROW_REGEX = Regex("^\\|?.*\\|.*\\|?.*$")
        private val TABLE_SEPARATOR_REGEX = Regex("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?$")
        private val WORD_REGEX = Regex("\\p{L}[\\p{L}\\p{N}]*")
        private val DIGIT_REGEX = Regex("\\d")
        private val SENTENCE_PUNCTUATION_REGEX = Regex("[.!?]")
    }
}

private data class Fence(
    val marker: Char,
    val length: Int
)
