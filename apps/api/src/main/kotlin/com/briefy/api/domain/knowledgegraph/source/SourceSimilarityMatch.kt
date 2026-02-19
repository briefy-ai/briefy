package com.briefy.api.domain.knowledgegraph.source

import java.util.UUID

data class SourceSimilarityMatch(
    val sourceId: UUID,
    val score: Double,
    val title: String?,
    val urlNormalized: String,
    val wordCount: Int
)
