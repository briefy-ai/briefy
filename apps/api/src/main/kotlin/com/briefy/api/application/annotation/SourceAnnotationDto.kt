package com.briefy.api.application.annotation

import com.briefy.api.domain.knowledgegraph.annotation.SourceAnnotation
import java.time.Instant
import java.util.UUID

data class CreateSourceAnnotationCommand(
    val body: String,
    val anchorQuote: String,
    val anchorPrefix: String,
    val anchorSuffix: String,
    val anchorStart: Int,
    val anchorEnd: Int
)

data class UpdateSourceAnnotationCommand(
    val body: String
)

data class SourceAnnotationResponse(
    val id: UUID,
    val sourceId: UUID,
    val body: String,
    val anchorQuote: String,
    val anchorPrefix: String,
    val anchorSuffix: String,
    val anchorStart: Int,
    val anchorEnd: Int,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant
)

fun SourceAnnotation.toResponse(): SourceAnnotationResponse = SourceAnnotationResponse(
    id = id,
    sourceId = sourceId,
    body = body,
    anchorQuote = anchorQuote,
    anchorPrefix = anchorPrefix,
    anchorSuffix = anchorSuffix,
    anchorStart = anchorStart,
    anchorEnd = anchorEnd,
    status = status.name.lowercase(),
    createdAt = createdAt,
    updatedAt = updatedAt
)
