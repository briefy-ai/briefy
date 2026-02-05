package com.briefy.api.application.source

import com.briefy.api.domain.knowledgegraph.source.*
import java.time.Instant
import java.util.UUID

data class CreateSourceCommand(
    val url: String
)

data class SourceResponse(
    val id: UUID,
    val url: UrlDto,
    val status: String,
    val content: ContentDto?,
    val metadata: MetadataDto?,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class UrlDto(
    val raw: String,
    val normalized: String,
    val platform: String
)

data class ContentDto(
    val text: String,
    val wordCount: Int
)

data class MetadataDto(
    val title: String?,
    val author: String?,
    val publishedDate: Instant?,
    val platform: String?,
    val estimatedReadingTime: Int?
)

fun Source.toResponse(): SourceResponse = SourceResponse(
    id = id,
    url = url.toDto(),
    status = status.name.lowercase(),
    content = content?.toDto(),
    metadata = metadata?.toDto(),
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Url.toDto(): UrlDto = UrlDto(
    raw = raw,
    normalized = normalized,
    platform = platform
)

fun Content.toDto(): ContentDto = ContentDto(
    text = text,
    wordCount = wordCount
)

fun Metadata.toDto(): MetadataDto = MetadataDto(
    title = title,
    author = author,
    publishedDate = publishedDate,
    platform = platform,
    estimatedReadingTime = estimatedReadingTime
)
