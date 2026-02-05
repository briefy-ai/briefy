package com.briefy.api.infrastructure.extraction

import java.time.Instant

data class ExtractionResult(
    val text: String,
    val title: String?,
    val author: String?,
    val publishedDate: Instant?
)
