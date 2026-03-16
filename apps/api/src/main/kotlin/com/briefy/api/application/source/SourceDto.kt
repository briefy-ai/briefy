package com.briefy.api.application.source

import com.briefy.api.domain.knowledgegraph.source.*
import java.time.Instant
import java.util.UUID

data class CreateSourceCommand(
    val url: String
)

data class ProvideSourceContentCommand(
    val rawText: String,
    val title: String? = null
)

data class SourceResponse(
    val id: UUID,
    val url: UrlDto,
    val status: String,
    val sourceType: String,
    val hasGeneratedCoverImage: Boolean,
    val content: ContentDto?,
    val metadata: MetadataDto?,
    val narrationState: String,
    val narrationFailureReason: String?,
    val narrationFailureMessage: String?,
    val narrationFailureRetryable: Boolean?,
    val audio: AudioContentDto?,
    val topicExtractionState: String,
    val topicExtractionFailureReason: String?,
    val pendingSuggestedTopicsCount: Long,
    val read: Boolean,
    val reuse: ReuseInfoDto?,
    val topics: List<SourceTopicChipDto> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant
)

data class SourcePageResponse(
    val items: List<SourceResponse>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val limit: Int
)

data class ReuseInfoDto(
    val usedCache: Boolean,
    val cacheAgeSeconds: Long?,
    val freshnessTtlSeconds: Long
)

data class SourceTopicChipDto(
    val id: UUID,
    val name: String
)

data class SourceSearchResponse(
    val items: List<SourceSearchResultDto>
)

data class SourceSearchResultDto(
    val id: UUID,
    val title: String?,
    val author: String?,
    val domain: String?,
    val sourceType: String,
    val topics: List<SourceTopicChipDto>
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
    val formattingState: String,
    val formattingFailureReason: String?,
    val ogImageUrl: String?,
    val videoId: String?,
    val videoEmbedUrl: String?,
    val videoDurationSeconds: Int?,
    val transcriptSource: String?,
    val transcriptLanguage: String?
)

data class AudioContentDto(
    val audioUrl: String,
    val durationSeconds: Int,
    val format: String,
    val contentHash: String,
    val generatedAt: Instant
)

data class NarrationEstimateResponse(
    val characterCount: Int,
    val provider: String,
    val modelId: String,
    val estimatedCostUsd: java.math.BigDecimal
)

fun Source.toResponse(
    pendingSuggestedTopicsCount: Long = 0,
    reuseInfo: ReuseInfoDto? = null,
    topics: List<SourceTopicChipDto> = emptyList()
): SourceResponse = SourceResponse(
    narrationState = narrationState.name.lowercase(),
    narrationFailureReason = narrationFailureReason,
    narrationFailureMessage = NarrationFailureCatalog.messageFor(narrationFailureReason),
    narrationFailureRetryable = NarrationFailureCatalog.retryableFor(narrationFailureReason),
    id = id,
    url = url.toDto(),
    status = status.name.lowercase(),
    sourceType = sourceType.name.lowercase(),
    hasGeneratedCoverImage = hasGeneratedCoverImage(),
    content = content?.toDto(),
    metadata = metadata?.toDto(),
    audio = audioContent?.toDto(),
    topicExtractionState = topicExtractionState.name.lowercase(),
    topicExtractionFailureReason = topicExtractionFailureReason,
    pendingSuggestedTopicsCount = pendingSuggestedTopicsCount,
    read = isRead,
    reuse = reuseInfo,
    topics = topics,
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
    formattingState = formattingState.name.lowercase(),
    formattingFailureReason = formattingFailureReason,
    ogImageUrl = ogImageUrl,
    videoId = videoId,
    videoEmbedUrl = videoEmbedUrl,
    videoDurationSeconds = videoDurationSeconds,
    transcriptSource = transcriptSource,
    transcriptLanguage = transcriptLanguage
)

fun AudioContent.toDto(): AudioContentDto = AudioContentDto(
    audioUrl = audioUrl,
    durationSeconds = durationSeconds,
    format = format,
    contentHash = contentHash,
    generatedAt = generatedAt
)

private object NarrationFailureCatalog {
    fun messageFor(code: String?): String? {
        return when (code) {
            null -> null
            "paid_plan_required" -> "Your ElevenLabs API key cannot use the configured voice. Free ElevenLabs plans cannot use library voices via API."
            "invalid_api_key" -> "Your ElevenLabs API key is invalid. Update it in Settings and try again."
            "quota_exceeded" -> "Your ElevenLabs quota has been exceeded. Check your ElevenLabs account and try again."
            "too_many_concurrent_requests", "system_busy", "voice_not_ready" ->
                "ElevenLabs is temporarily unable to generate audio. Try again shortly."
            "tts_provider_not_configured", "elevenlabs_not_configured" -> "Your preferred TTS provider is not configured in Settings."
            "inworld_invalid_api_key" -> "Your Inworld API key is invalid. Update it in Settings and try again."
            "inworld_rate_limited" -> "Inworld is temporarily unable to generate audio. Try again shortly."
            "content_too_long" -> "This source is too long to narrate with the current limits."
            "empty_plaintext_content" -> "This source does not contain narratable text."
            "source_audio_download_failed", "source_audio_storage_failed", "source_audio_url_refresh_failed" ->
                "Briefy could not prepare the original video audio. Try again."
            "audio_storage_failed", "audio_url_refresh_failed", "tts_generation_failed", "elevenlabs_server_error", "elevenlabs_request_retryable", "inworld_server_error", "inworld_request_retryable" ->
                "Briefy could not generate audio for this source. Try again."
            "elevenlabs_request_failed", "inworld_request_failed" -> "Briefy could not generate audio for this source."
            else -> "Briefy could not generate audio for this source."
        }
    }

    fun retryableFor(code: String?): Boolean? {
        return when (code) {
            null -> null
            "paid_plan_required", "invalid_api_key", "quota_exceeded", "tts_provider_not_configured", "elevenlabs_not_configured", "content_too_long", "empty_plaintext_content", "elevenlabs_request_failed", "inworld_invalid_api_key", "inworld_request_failed" -> false
            "too_many_concurrent_requests", "system_busy", "voice_not_ready", "audio_storage_failed", "audio_url_refresh_failed", "tts_generation_failed", "elevenlabs_server_error", "elevenlabs_request_retryable", "inworld_server_error", "inworld_request_retryable", "inworld_rate_limited" -> true
            "source_audio_download_failed", "source_audio_storage_failed", "source_audio_url_refresh_failed" -> true
            else -> false
        }
    }
}
