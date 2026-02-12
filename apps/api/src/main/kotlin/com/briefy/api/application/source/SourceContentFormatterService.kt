package com.briefy.api.application.source

import com.briefy.api.application.settings.UserAiSettingsService
import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.SharedSourceSnapshot
import com.briefy.api.domain.knowledgegraph.source.SharedSourceSnapshotRepository
import com.briefy.api.domain.knowledgegraph.source.SharedSourceSnapshotStatus
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.infrastructure.extraction.ExtractionProviderId
import com.briefy.api.infrastructure.formatting.ExtractionContentFormatter
import com.briefy.api.infrastructure.id.IdGenerator
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
    private val idGenerator: IdGenerator
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
            logger.info("[formatter] skipped sourceId={} userId={} extractorId={} reason=already_formatted", sourceId, userId, extractorId)
            return
        }

        val formatter = extractionContentFormatters.firstOrNull { it.supports(extractorId) }
        if (formatter == null) {
            logger.info("[formatter] skipped sourceId={} userId={} extractorId={} reason=unsupported_extractor", sourceId, userId, extractorId)
            return
        }

        val aiSelection = try {
            userAiSettingsService.resolveUseCaseSelection(userId, UserAiSettingsService.SOURCE_FORMATTING)
        } catch (e: Exception) {
            logger.warn(
                "[formatter] formatting_failed sourceId={} userId={} extractorId={} reason=ai_settings_resolution_failed",
                sourceId,
                userId,
                extractorId,
                e
            )
            return
        }

        val formattedText = try {
            formatter.format(content.text, aiSelection.provider, aiSelection.model)
        } catch (e: Exception) {
            logger.warn("[formatter] formatting_failed sourceId={} userId={} extractorId={} reason=llm_error", sourceId, userId, extractorId, e)
            return
        }

        if (formattedText.isBlank()) {
            logger.warn("[formatter] formatting_failed sourceId={} userId={} extractorId={} reason=empty_formatted_content", sourceId, userId, extractorId)
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
            extractionProvider = metadata.extractionProvider
        )

        source.content = formattedContent
        source.metadata = formattedMetadata
        source.updatedAt = Instant.now()
        sourceRepository.save(source)

        maybeUpdateLatestSnapshot(source, formattedContent, formattedMetadata, extractorId)

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
}
