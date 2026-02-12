package com.briefy.api.domain.knowledgegraph.source

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.time.Instant

@Embeddable
data class Metadata(
    @Column(name = "metadata_title", length = 500)
    val title: String? = null,

    @Column(name = "metadata_author", length = 255)
    val author: String? = null,

    @Column(name = "metadata_published_date")
    val publishedDate: Instant? = null,

    @Column(name = "metadata_platform", length = 50)
    val platform: String? = null,

    @Column(name = "metadata_estimated_reading_time")
    val estimatedReadingTime: Int? = null,

    @Column(name = "metadata_ai_formatted")
    val aiFormatted: Boolean = false,

    @Column(name = "metadata_extraction_provider", length = 50)
    val extractionProvider: String? = null
) {
    companion object {
        private const val WORDS_PER_MINUTE = 200

        fun from(
            title: String?,
            author: String?,
            publishedDate: Instant?,
            platform: String?,
            wordCount: Int,
            aiFormatted: Boolean,
            extractionProvider: String?
        ): Metadata {
            val readingTime = if (wordCount > 0) {
                (wordCount / WORDS_PER_MINUTE).coerceAtLeast(1)
            } else {
                null
            }

            return Metadata(
                title = title?.take(500),
                author = author?.take(255),
                publishedDate = publishedDate,
                platform = platform?.take(50),
                estimatedReadingTime = readingTime,
                aiFormatted = aiFormatted,
                extractionProvider = extractionProvider?.take(50)
            )
        }
    }
}
