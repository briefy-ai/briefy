package com.briefy.api.domain.knowledgegraph.source

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class Content(
    @Column(name = "content_text", columnDefinition = "TEXT")
    val text: String,

    @Column(name = "content_word_count")
    val wordCount: Int
) {
    companion object {
        fun from(text: String): Content {
            return Content(
                text = text,
                wordCount = countWords(text)
            )
        }

        fun countWords(text: String): Int {
            if (text.isBlank()) return 0
            return text.trim()
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .size
        }
    }
}
