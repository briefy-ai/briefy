package com.briefy.api.application.sharing

import java.awt.FontMetrics

object TextLayoutHelper {
    fun wrapText(text: String, fontMetrics: FontMetrics, maxWidth: Int, maxLines: Int): List<String> {
        val normalizedText = text.replace(Regex("\\s+"), " ").trim().ifBlank { "Briefy AI" }
        val words = normalizedText.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        fun flushLine() {
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
                currentLine = StringBuilder()
            }
        }

        for (word in words) {
            val candidate = if (currentLine.isEmpty()) word else "${currentLine} $word"
            if (fontMetrics.stringWidth(candidate) <= maxWidth) {
                currentLine = StringBuilder(candidate)
                continue
            }

            flushLine()
            if (lines.size == maxLines) {
                break
            }

            if (fontMetrics.stringWidth(word) <= maxWidth) {
                currentLine.append(word)
            } else {
                currentLine.append(ellipsize(word, fontMetrics, maxWidth))
                flushLine()
            }
        }
        flushLine()

        if (lines.isEmpty()) {
            return listOf("Briefy AI")
        }
        if (lines.size > maxLines) {
            return lines.take(maxLines)
        }
        if (lines.size == maxLines && words.joinToString(" ") != lines.joinToString(" ")) {
            lines[maxLines - 1] = forceEllipsis(lines[maxLines - 1], fontMetrics, maxWidth)
        }
        return lines
    }

    fun ellipsize(text: String, fontMetrics: FontMetrics, maxWidth: Int): String {
        if (fontMetrics.stringWidth(text) <= maxWidth) {
            return text
        }
        val ellipsis = "..."
        var candidate = text
        while (candidate.isNotEmpty() && fontMetrics.stringWidth("$candidate$ellipsis") > maxWidth) {
            candidate = candidate.dropLast(1)
        }
        return if (candidate.isEmpty()) ellipsis else "$candidate$ellipsis"
    }

    private fun forceEllipsis(text: String, fontMetrics: FontMetrics, maxWidth: Int): String {
        val ellipsis = "..."
        if (fontMetrics.stringWidth("$text$ellipsis") <= maxWidth) {
            return "$text$ellipsis"
        }
        return ellipsize(text, fontMetrics, maxWidth)
    }
}
