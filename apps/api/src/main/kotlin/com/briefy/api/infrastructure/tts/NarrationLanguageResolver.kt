package com.briefy.api.infrastructure.tts

import org.springframework.stereotype.Component

@Component
class NarrationLanguageResolver(
    private val narrationScriptPreparer: NarrationScriptPreparer
) {
    fun resolve(transcriptLanguage: String?, contentText: String?): String {
        normalizeLanguageCode(transcriptLanguage)?.let { return it }
        return detectFromText(contentText.orEmpty())
    }

    private fun normalizeLanguageCode(raw: String?): String? {
        val normalized = raw
            ?.trim()
            ?.lowercase()
            ?.substringBefore('-')
            ?.substringBefore('_')
            ?.ifBlank { null }
            ?: return null

        return when (normalized) {
            LANGUAGE_ENGLISH, LANGUAGE_SPANISH -> normalized
            else -> null
        }
    }

    private fun detectFromText(contentText: String): String {
        val plainText = narrationScriptPreparer.prepare(contentText).lowercase()
        if (plainText.isBlank()) return LANGUAGE_ENGLISH

        val tokens = WORD_PATTERN.findAll(plainText)
            .map { it.value }
            .toList()

        val spanishScore = tokens.count { it in SPANISH_MARKERS } +
            if (SPANISH_PUNCTUATION_PATTERN.containsMatchIn(plainText)) 2 else 0 +
            if (SPANISH_ACCENT_PATTERN.containsMatchIn(plainText)) 2 else 0
        val englishScore = tokens.count { it in ENGLISH_MARKERS }

        return when {
            spanishScore >= englishScore + 1 && spanishScore >= 2 -> LANGUAGE_SPANISH
            englishScore >= spanishScore + 1 && englishScore >= 2 -> LANGUAGE_ENGLISH
            SPANISH_PUNCTUATION_PATTERN.containsMatchIn(plainText) || SPANISH_ACCENT_PATTERN.containsMatchIn(plainText) -> LANGUAGE_SPANISH
            else -> LANGUAGE_ENGLISH
        }
    }

    companion object {
        private const val LANGUAGE_ENGLISH = "en"
        private const val LANGUAGE_SPANISH = "es"
        private val WORD_PATTERN = Regex("\\p{L}+")
        private val SPANISH_PUNCTUATION_PATTERN = Regex("[¡¿]")
        private val SPANISH_ACCENT_PATTERN = Regex("[áéíóúñü]")
        private val ENGLISH_MARKERS = setOf(
            "the", "and", "that", "this", "with", "from", "have", "your", "for", "are",
            "you", "not", "was", "but", "they", "will", "about", "into", "there", "their"
        )
        private val SPANISH_MARKERS = setOf(
            "que", "los", "las", "para", "con", "una", "por", "como", "pero", "más",
            "este", "esta", "del", "sus", "porque", "también", "entre", "desde", "cuando", "sobre"
        )
    }
}
