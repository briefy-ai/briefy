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
    val sourceType: String,
    val content: ContentDto?,
    val metadata: MetadataDto?,
    val reuse: ReuseInfoDto?,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class ReuseInfoDto(
    val usedCache: Boolean,
    val cacheAgeSeconds: Long?,
    val freshnessTtlSeconds: Long
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
    val estimatedReadingTime: Int?,
    val aiFormatted: Boolean,
    val extractionProvider: String?,
    val videoId: String?,
    val videoEmbedUrl: String?,
    val videoDurationSeconds: Int?,
    val transcriptSource: String?,
    val transcriptLanguage: String?
)

fun Source.toResponse(reuseInfo: ReuseInfoDto? = null): SourceResponse = SourceResponse(
    id = id,
    url = url.toDto(),
    status = status.name.lowercase(),
    sourceType = sourceType.name.lowercase(),
    content = content?.toDto(),
    metadata = metadata?.toDto(),
    reuse = reuseInfo,
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
    estimatedReadingTime = estimatedReadingTime,
    aiFormatted = aiFormatted,
    extractionProvider = extractionProvider,
    videoId = videoId,
    videoEmbedUrl = videoEmbedUrl,
    videoDurationSeconds = videoDurationSeconds,
    transcriptSource = transcriptSource,
    transcriptLanguage = transcriptLanguage
)
