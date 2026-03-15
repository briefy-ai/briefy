package com.briefy.api.infrastructure.tts

object TtsTextChunker {
    fun split(text: String, maxCharacters: Int): List<String> {
        require(maxCharacters > 0) { "maxCharacters must be positive" }

        val normalized = text.replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return emptyList()
        if (normalized.length <= maxCharacters) return listOf(normalized)

        val segments = mutableListOf<String>()
        val current = StringBuilder()

        splitSentences(normalized).forEach { sentence ->
            if (sentence.length > maxCharacters) {
                flush(current, segments)
                splitLongSentence(sentence, maxCharacters).forEach(segments::add)
                return@forEach
            }

            val nextLength = if (current.isEmpty()) sentence.length else current.length + 1 + sentence.length
            if (nextLength > maxCharacters) {
                flush(current, segments)
            }
            if (current.isNotEmpty()) {
                current.append(' ')
            }
            current.append(sentence)
        }

        flush(current, segments)
        return segments
    }

    private fun splitSentences(text: String): List<String> {
        return text
            .split(Regex("(?<=[.!?])\\s+"))
            .map(String::trim)
            .filter(String::isNotBlank)
    }

    private fun splitLongSentence(text: String, maxCharacters: Int): List<String> {
        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        text.split(" ").filter(String::isNotBlank).forEach { word ->
            if (word.length > maxCharacters) {
                flush(current, chunks)
                word.chunked(maxCharacters).forEach(chunks::add)
                return@forEach
            }

            val nextLength = if (current.isEmpty()) word.length else current.length + 1 + word.length
            if (nextLength > maxCharacters) {
                flush(current, chunks)
            }
            if (current.isNotEmpty()) {
                current.append(' ')
            }
            current.append(word)
        }

        flush(current, chunks)
        return chunks
    }

    private fun flush(current: StringBuilder, output: MutableList<String>) {
        if (current.isEmpty()) return
        output.add(current.toString())
        current.setLength(0)
    }
}
