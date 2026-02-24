package com.briefy.api.application.source

import com.briefy.api.application.settings.AiModelSelection
import com.briefy.api.application.settings.UserAiSettingsService
import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.FormattingState
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.SharedSourceSnapshot
import com.briefy.api.domain.knowledgegraph.source.SharedSourceSnapshotRepository
import com.briefy.api.domain.knowledgegraph.source.SharedSourceSnapshotStatus
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.domain.knowledgegraph.source.event.SourceContentFinalizedEvent
import com.briefy.api.infrastructure.extraction.ExtractionProviderId
import com.briefy.api.infrastructure.formatting.ExtractionContentFormatter
import com.briefy.api.infrastructure.id.IdGenerator
import org.springframework.context.ApplicationEventPublisher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class SourceContentFormatterService(
    private val sourceRepository: SourceRepository,
    private val sharedSourceSnapshotRepository: SharedSourceSnapshotRepository,
    private val extractionContentFormatters: List<ExtractionContentFormatter>,
    private val userAiSettingsService: UserAiSettingsService,
    private val idGenerator: IdGenerator,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val logger = LoggerFactory.getLogger(SourceContentFormatterService::class.java)

    @Transactional
    fun formatSourceContent(sourceId: UUID, userId: UUID, extractorId: ExtractionProviderId) {
        val source = sourceRepository.findByIdAndUserId(sourceId, userId)
        if (source == null) {
            logger.info("[formatter] skipped sourceId={} userId={} extractorId={} reason=source_not_found", sourceId, userId, extractorId)
            return
        }

        if (source.status != SourceStatus.ACTIVE) {
            logger.info("[formatter] skipped sourceId={} userId={} extractorId={} reason=source_not_active status={}", sourceId, userId, extractorId, source.status)
            return
        }

        val content = source.content
        val metadata = source.metadata
        if (content == null || metadata == null || content.text.isBlank()) {
            logger.info("[formatter] skipped sourceId={} userId={} extractorId={} reason=empty_content", sourceId, userId, extractorId)
            return
        }

        if (metadata.aiFormatted) {
            if (metadata.formattingState != FormattingState.SUCCEEDED || metadata.formattingFailureReason != null) {
                source.metadata = metadata.withFormattingState(FormattingState.SUCCEEDED)
                source.updatedAt = Instant.now()
                sourceRepository.save(source)
            }
            logger.info("[formatter] skipped sourceId={} userId={} extractorId={} reason=already_formatted", sourceId, userId, extractorId)
            publishSourceContentFinalizedEvent(source.id, source.userId)
            return
        }

        if (extractorId == ExtractionProviderId.YOUTUBE && !metadata.transcriptSource.equals("captions", ignoreCase = true)) {
            source.metadata = metadata.withFormattingState(FormattingState.NOT_REQUIRED)
            source.updatedAt = Instant.now()
            sourceRepository.save(source)
            logger.info(
                "[formatter] skipped sourceId={} userId={} extractorId={} reason=youtube_non_caption_transcript transcriptSource={}",
                sourceId,
                userId,
                extractorId,
                metadata.transcriptSource
            )
            publishSourceContentFinalizedEvent(source.id, source.userId)
            return
        }

        val formatter = extractionContentFormatters.firstOrNull { it.supports(extractorId) }
        if (formatter == null) {
            source.metadata = metadata.withFormattingState(FormattingState.NOT_REQUIRED)
            source.updatedAt = Instant.now()
            sourceRepository.save(source)
            logger.info("[formatter] skipped sourceId={} userId={} extractorId={} reason=unsupported_extractor", sourceId, userId, extractorId)
            publishSourceContentFinalizedEvent(source.id, source.userId)
            return
        }

        val aiSelection = try {
            resolveModelSelection(extractorId, userId)
        } catch (e: Exception) {
            source.metadata = metadata.withFormattingState(FormattingState.FAILED, REASON_AI_SETTINGS_RESOLUTION_FAILED)
            source.updatedAt = Instant.now()
            sourceRepository.save(source)
            logger.warn(
                "[formatter] formatting_failed sourceId={} userId={} extractorId={} reason={}",
                sourceId,
                userId,
                extractorId,
                REASON_AI_SETTINGS_RESOLUTION_FAILED,
                e
            )
            return
        }

        val formattedText = try {
            formatter.format(content.text, aiSelection.provider, aiSelection.model)
        } catch (e: Exception) {
            source.metadata = metadata.withFormattingState(FormattingState.FAILED, REASON_LLM_ERROR)
            source.updatedAt = Instant.now()
            sourceRepository.save(source)
            logger.warn("[formatter] formatting_failed sourceId={} userId={} extractorId={} reason={}", sourceId, userId, extractorId, REASON_LLM_ERROR, e)
            return
        }

        if (formattedText.isBlank()) {
            source.metadata = metadata.withFormattingState(FormattingState.FAILED, REASON_EMPTY_FORMATTED_CONTENT)
            source.updatedAt = Instant.now()
            sourceRepository.save(source)
            logger.warn(
                "[formatter] formatting_failed sourceId={} userId={} extractorId={} reason={}",
                sourceId,
                userId,
                extractorId,
                REASON_EMPTY_FORMATTED_CONTENT
            )
            return
        }

        val formattedContent = Content.from(formattedText)
        val formattedMetadata = Metadata.from(
            title = metadata.title,
            author = metadata.author,
            publishedDate = metadata.publishedDate,
            platform = metadata.platform,
            wordCount = formattedContent.wordCount,
            aiFormatted = true,
            extractionProvider = metadata.extractionProvider,
            formattingState = FormattingState.SUCCEEDED,
            formattingFailureReason = null,
            videoId = metadata.videoId,
            videoEmbedUrl = metadata.videoEmbedUrl,
            videoDurationSeconds = metadata.videoDurationSeconds,
            transcriptSource = metadata.transcriptSource,
            transcriptLanguage = metadata.transcriptLanguage
        )

        source.content = formattedContent
        source.metadata = formattedMetadata
        source.updatedAt = Instant.now()
        sourceRepository.save(source)

        maybeUpdateLatestSnapshot(source, formattedContent, formattedMetadata, extractorId)
        publishSourceContentFinalizedEvent(source.id, source.userId)

        logger.info(
            "[formatter] formatting_succeeded sourceId={} userId={} extractorId={} provider={} model={} originalWordCount={} formattedWordCount={}",
            source.id,
            source.userId,
            extractorId,
            aiSelection.provider,
            aiSelection.model,
            content.wordCount,
            formattedContent.wordCount
        )
    }

    private fun maybeUpdateLatestSnapshot(
        source: Source,
        formattedContent: Content,
        formattedMetadata: Metadata,
        extractorId: ExtractionProviderId
    ) {
        val latestSnapshot = sharedSourceSnapshotRepository.findFirstByUrlNormalizedAndIsLatestTrue(source.url.normalized)
        if (latestSnapshot == null) {
            logger.info("[formatter] snapshot_skipped sourceId={} extractorId={} reason=no_latest_snapshot", source.id, extractorId)
            return
        }

        val latestMetadata = latestSnapshot.metadata
        if (
            latestSnapshot.status != SharedSourceSnapshotStatus.ACTIVE ||
            latestSnapshot.content == null ||
            latestMetadata == null
        ) {
            logger.info("[formatter] snapshot_skipped sourceId={} extractorId={} reason=latest_snapshot_not_eligible", source.id, extractorId)
            return
        }

        if (latestMetadata.aiFormatted) {
            logger.info("[formatter] snapshot_skipped sourceId={} extractorId={} reason=already_formatted", source.id, extractorId)
            return
        }

        if (!latestMetadata.extractionProvider.equals(extractorId.name.lowercase(), ignoreCase = true)) {
            logger.info(
                "[formatter] snapshot_skipped sourceId={} extractorId={} reason=provider_mismatch snapshotProvider={}",
                source.id,
                extractorId,
                latestMetadata.extractionProvider
            )
            return
        }

        val updatedAt = Instant.now()
        sharedSourceSnapshotRepository.markLatestAsNotLatest(source.url.normalized, updatedAt)
        val nextVersion = sharedSourceSnapshotRepository.findMaxVersionByUrlNormalized(source.url.normalized) + 1

        val formattedSnapshot = SharedSourceSnapshot(
            id = idGenerator.newId(),
            urlNormalized = latestSnapshot.urlNormalized,
            sourceType = latestSnapshot.sourceType,
            status = latestSnapshot.status,
            content = formattedContent,
            metadata = formattedMetadata,
            fetchedAt = latestSnapshot.fetchedAt,
            expiresAt = latestSnapshot.expiresAt,
            version = nextVersion,
            isLatest = true,
            createdAt = updatedAt,
            updatedAt = updatedAt
        )

        sharedSourceSnapshotRepository.save(formattedSnapshot)
        logger.info(
            "[formatter] snapshot_updated sourceId={} snapshotId={} snapshotVersion={} extractorId={}",
            source.id,
            formattedSnapshot.id,
            formattedSnapshot.version,
            extractorId
        )
    }

    private fun resolveModelSelection(extractorId: ExtractionProviderId, userId: UUID): AiModelSelection {
        if (extractorId == ExtractionProviderId.YOUTUBE) {
            return AiModelSelection(
                provider = YOUTUBE_FORMATTER_PROVIDER,
                model = YOUTUBE_FORMATTER_MODEL
            )
        }
        return userAiSettingsService.resolveUseCaseSelection(userId, UserAiSettingsService.SOURCE_FORMATTING)
    }

    private fun publishSourceContentFinalizedEvent(sourceId: UUID, userId: UUID) {
        eventPublisher.publishEvent(
            SourceContentFinalizedEvent(
                sourceId = sourceId,
                userId = userId
            )
        )
    }

    companion object {
        private const val YOUTUBE_FORMATTER_PROVIDER = "google_genai"
        private const val YOUTUBE_FORMATTER_MODEL = "gemini-2.5-flash-lite"
        private const val REASON_AI_SETTINGS_RESOLUTION_FAILED = "ai_settings_resolution_failed"
        private const val REASON_LLM_ERROR = "llm_error"
        private const val REASON_EMPTY_FORMATTED_CONTENT = "empty_formatted_content"
    }
}
