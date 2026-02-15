package com.briefy.api.infrastructure.extraction

import java.time.Instant

data class ExtractionResult(
    val text: String,
    val title: String?,
    val author: String?,
    val publishedDate: Instant?,
    val aiFormatted: Boolean = false,
    val videoId: String? = null,
    val videoEmbedUrl: String? = null,
    val videoDurationSeconds: Int? = null,
    val transcriptSource: String? = null,
    val transcriptLanguage: String? = null
)
