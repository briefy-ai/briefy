package com.briefy.api.application.source

import com.briefy.api.domain.knowledgegraph.source.*
import com.briefy.api.domain.knowledgegraph.source.event.SourceArchivedEvent
import com.briefy.api.domain.knowledgegraph.source.event.SourceActivatedEvent
import com.briefy.api.domain.knowledgegraph.source.event.SourceActivationReason
import com.briefy.api.domain.knowledgegraph.source.event.SourceContentFormattingRequestedEvent
import com.briefy.api.domain.knowledgegraph.source.event.SourceRestoredEvent
import com.briefy.api.domain.knowledgegraph.topic.TopicRepository
import com.briefy.api.domain.knowledgegraph.topic.TopicStatus
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkRepository
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkStatus
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkTargetType
import com.briefy.api.infrastructure.extraction.ExtractionProviderId
import com.briefy.api.infrastructure.extraction.ExtractionProviderResolver
import com.briefy.api.infrastructure.id.IdGenerator
import com.briefy.api.infrastructure.security.CurrentUserProvider
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class SourceService(
    private val sourceRepository: SourceRepository,
    private val sharedSourceSnapshotRepository: SharedSourceSnapshotRepository,
    private val sourceExtractionJobService: SourceExtractionJobService,
    private val topicRepository: TopicRepository,
    private val topicLinkRepository: TopicLinkRepository,
    private val sourceDependencyChecker: SourceDependencyChecker,
    private val extractionProviderResolver: ExtractionProviderResolver,
    private val sourceTypeClassifier: SourceTypeClassifier,
    private val freshnessPolicy: FreshnessPolicy,
    private val currentUserProvider: CurrentUserProvider,
    private val idGenerator: IdGenerator,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val logger = LoggerFactory.getLogger(SourceService::class.java)

    @Transactional
    fun submitSource(command: CreateSourceCommand): SourceResponse {
        return submitSourceForUser(currentUserProvider.requireUserId(), command)
    }

    @Transactional
    fun submitSourceForUser(userId: UUID, command: CreateSourceCommand): SourceResponse {
        val normalizedUrl = Url.normalize(command.url)
        val sharedUrlSourceCount = sourceRepository.countByUrlNormalized(normalizedUrl)
        logger.info("[service] Submitting source userId={} url={}", userId, normalizedUrl)

        sourceRepository.findByUserIdAndUrlNormalized(userId, normalizedUrl)?.let { existing ->
            throw SourceAlreadyExistsException(normalizedUrl, existing.id)
        }

        val sourceType = sourceTypeClassifier.classify(normalizedUrl)
        val freshnessTtlSeconds = freshnessPolicy.ttlSeconds(sourceType)
        val now = Instant.now()
        val latestSnapshot = sharedSourceSnapshotRepository.findFirstByUrlNormalizedAndIsLatestTrue(normalizedUrl)
        val snapshotCacheAge = latestSnapshot?.let { cacheAgeSeconds(it.fetchedAt, now) }

        logger.info(
            "[service] Source submit received url={} userId={} sourceType={} sharedUrlSourceCount={}",
            normalizedUrl,
            userId,
            sourceType,
            sharedUrlSourceCount
        )

        if (isReusableSnapshot(latestSnapshot, now)) {
            return buildCacheHitResponse(
                command = command,
                userId = userId,
                normalizedUrl = normalizedUrl,
                latestSnapshot = latestSnapshot,
                snapshotCacheAge = snapshotCacheAge
            )
        }

        if (latestSnapshot == null) {
            logger.info("[service] Cache miss url={} reason=no_snapshot fetching_fresh=true", normalizedUrl)
        } else {
            logger.info(
                "[service] Cache miss url={} reason=stale_or_invalid snapshotId={} snapshotVersion={} snapshotStatus={} expiresAt={} now={} fetching_fresh=true",
                normalizedUrl,
                latestSnapshot.id,
                latestSnapshot.version,
                latestSnapshot.status,
                latestSnapshot.expiresAt,
                now
            )
        }

        val source = Source.create(idGenerator.newId(), command.url, userId, sourceType)
        sourceRepository.save(source)

        if (sourceType == SourceType.VIDEO) {
            sourceExtractionJobService.enqueueYoutubeExtraction(source.id, source.userId, Instant.now())
            logger.info("[service] Enqueued youtube extraction sourceId={} userId={}", source.id, source.userId)
            return source.toResponse(
                reuseInfo = ReuseInfoDto(
                    usedCache = false,
                    cacheAgeSeconds = snapshotCacheAge,
                    freshnessTtlSeconds = freshnessTtlSeconds
                )
            )
        }

        val extractedSource = extractContent(source)

        return extractedSource.toResponse(
            reuseInfo = ReuseInfoDto(
                usedCache = false,
                cacheAgeSeconds = snapshotCacheAge,
                freshnessTtlSeconds = freshnessTtlSeconds
            )
        )
    }

    @Transactional(readOnly = true)
    fun listSources(status: SourceStatus? = null): List<SourceResponse> {
        val userId = currentUserProvider.requireUserId()
        val effectiveStatus = status ?: SourceStatus.ACTIVE
        logger.info("[service] Listing sources userId={} status={}", userId, effectiveStatus)
        val sources = sourceRepository.findByUserIdAndStatus(userId, effectiveStatus)
        logger.info("[service] Listed sources userId={} count={}", userId, sources.size)
        return sources.map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun getSource(id: UUID): SourceResponse {
        val userId = currentUserProvider.requireUserId()
        logger.info("[service] Getting source userId={} sourceId={}", userId, id)
        val source = sourceRepository.findByIdAndUserId(id, userId)
            ?: throw SourceNotFoundException(id)
        logger.info("[service] Fetched source userId={} sourceId={} status={}", userId, source.id, source.status)
        return source.toResponse()
    }

    @Transactional
    fun retryExtraction(id: UUID): SourceResponse {
        val userId = currentUserProvider.requireUserId()
        logger.info("[service] Retrying extraction userId={} sourceId={}", userId, id)
        val source = sourceRepository.findByIdAndUserId(id, userId)
            ?: throw SourceNotFoundException(id)

        if (source.status != SourceStatus.FAILED) {
            throw InvalidSourceStateException("Can only retry extraction for failed sources. Current status: ${source.status}")
        }

        source.retry()
        sourceRepository.save(source)

        if (source.sourceType == SourceType.VIDEO) {
            sourceExtractionJobService.enqueueYoutubeExtraction(source.id, source.userId, Instant.now())
            return source.toResponse()
        }

        return extractContent(source).toResponse()
    }

    @Transactional
    fun processQueuedExtraction(sourceId: UUID, userId: UUID): SourceResponse {
        val source = sourceRepository.findByIdAndUserId(sourceId, userId)
            ?: throw SourceNotFoundException(sourceId)

        if (source.status == SourceStatus.ARCHIVED) {
            return source.toResponse()
        }
        if (source.status == SourceStatus.ACTIVE) {
            return source.toResponse()
        }
        if (source.status == SourceStatus.FAILED) {
            source.retry()
            sourceRepository.save(source)
        }

        return extractContent(source).toResponse()
    }

    @Transactional
    fun deleteSource(id: UUID) {
        val userId = currentUserProvider.requireUserId()
        logger.info("[service] Deleting source userId={} sourceId={}", userId, id)
        val source = sourceRepository.findByIdAndUserId(id, userId)
            ?: return

        if (sourceDependencyChecker.hasBlockingDependencies(sourceId = source.id, userId = userId)) {
            if (source.status != SourceStatus.ARCHIVED) {
                source.archive()
                sourceRepository.save(source)
                eventPublisher.publishEvent(SourceArchivedEvent(sourceId = source.id, userId = userId))
            }
            return
        }

        require(source.status in HARD_DELETE_ELIGIBLE_STATUSES) {
            "Can only hard-delete ACTIVE, FAILED, or ARCHIVED sources. Current status: ${source.status}"
        }

        hardDeleteSource(source)
    }

    @Transactional
    fun restoreSource(id: UUID) {
        val userId = currentUserProvider.requireUserId()
        logger.info("[service] Restoring source userId={} sourceId={}", userId, id)
        val source = sourceRepository.findByIdAndUserId(id, userId)
            ?: throw SourceNotFoundException(id)

        if (source.status == SourceStatus.ARCHIVED) {
            source.restore()
            sourceRepository.save(source)
            eventPublisher.publishEvent(SourceRestoredEvent(sourceId = source.id, userId = userId))
        }
    }

    @Transactional
    fun archiveSourcesBatch(sourceIds: List<UUID>) {
        val userId = currentUserProvider.requireUserId()
        val dedupedIds = sourceIds.distinct()
        require(dedupedIds.isNotEmpty()) { "sourceIds must not be empty" }
        require(dedupedIds.size <= MAX_BATCH_ARCHIVE_SOURCE_IDS) {
            "sourceIds must contain at most $MAX_BATCH_ARCHIVE_SOURCE_IDS ids"
        }

        logger.info(
            "[service] Batch archive sources userId={} requestedCount={} dedupedCount={}",
            userId,
            sourceIds.size,
            dedupedIds.size
        )

        val sources = sourceRepository.findAllByUserIdAndIdIn(userId, dedupedIds)
        if (sources.size != dedupedIds.size) {
            throw BatchSourceNotFoundException()
        }

        sources.forEach { source ->
            if (source.status != SourceStatus.ARCHIVED) {
                source.archive()
            }
        }
        sourceRepository.saveAll(sources)
    }

    companion object {
        private const val MAX_BATCH_ARCHIVE_SOURCE_IDS = 100
        private val HARD_DELETE_ELIGIBLE_STATUSES = setOf(
            SourceStatus.ACTIVE,
            SourceStatus.FAILED,
            SourceStatus.ARCHIVED
        )
        private val LIVE_TOPIC_LINK_STATUSES = listOf(
            TopicLinkStatus.SUGGESTED,
            TopicLinkStatus.ACTIVE
        )
    }

    private fun hardDeleteSource(source: Source) {
        val wasLastSourceForUrl = sourceRepository.countByUrlNormalized(source.url.normalized) == 1L
        val sourceTopicLinks = topicLinkRepository.findByUserIdAndTargetTypeAndTargetIdAndStatusIn(
            userId = source.userId,
            targetType = TopicLinkTargetType.SOURCE,
            targetId = source.id,
            statuses = LIVE_TOPIC_LINK_STATUSES
        )
        val affectedTopicIds = sourceTopicLinks.map { it.topicId }.toSet()

        if (sourceTopicLinks.isNotEmpty()) {
            topicLinkRepository.deleteAll(sourceTopicLinks)
        }
        deleteOrphanTopicsForSource(source.userId, affectedTopicIds)
        sourceRepository.delete(source)

        if (wasLastSourceForUrl) {
            sharedSourceSnapshotRepository.deleteByUrlNormalized(source.url.normalized)
        }

        // TODO: Emit SourceDeletedEvent once downstream consumers are implemented.
    }

    private fun deleteOrphanTopicsForSource(userId: UUID, topicIds: Set<UUID>) {
        if (topicIds.isEmpty()) {
            return
        }

        val topicsToDelete = topicRepository.findAllByIdInAndUserId(topicIds, userId)
            .filter { it.status == TopicStatus.SUGGESTED || it.status == TopicStatus.ACTIVE }
            .filter { topic ->
                topicLinkRepository.countByUserIdAndTopicIdAndStatusIn(
                    userId = userId,
                    topicId = topic.id,
                    statuses = LIVE_TOPIC_LINK_STATUSES
                ) == 0L
            }

        if (topicsToDelete.isNotEmpty()) {
            topicRepository.deleteAll(topicsToDelete)
        }
    }

    private fun extractContent(source: Source): Source {
        if (source.status == SourceStatus.SUBMITTED) {
            source.startExtraction()
            sourceRepository.save(source)
        } else if (source.status != SourceStatus.EXTRACTING) {
            throw InvalidSourceStateException("Source ${source.id} cannot be extracted from state ${source.status}")
        }

        try {
            val provider = extractionProviderResolver.resolveProvider(source.userId, source.url.platform)
            val result = provider.extract(source.url.normalized)
            val resolvedProvider = provider.id.name.lowercase()

            val content = Content.from(result.text)
            val metadata = Metadata.from(
                title = result.title,
                author = result.author,
                publishedDate = result.publishedDate,
                platform = source.url.platform,
                wordCount = content.wordCount,
                aiFormatted = result.aiFormatted,
                extractionProvider = resolvedProvider,
                videoId = result.videoId,
                videoEmbedUrl = result.videoEmbedUrl,
                videoDurationSeconds = result.videoDurationSeconds,
                transcriptSource = result.transcriptSource,
                transcriptLanguage = result.transcriptLanguage
            )

            source.completeExtraction(content, metadata)
            sourceRepository.save(source)
            saveSharedSnapshot(source, Instant.now())
            eventPublisher.publishEvent(
                SourceActivatedEvent(
                    sourceId = source.id,
                    userId = source.userId,
                    activationReason = SourceActivationReason.FRESH_EXTRACTION
                )
            )
            if (!result.aiFormatted) {
                eventPublisher.publishEvent(
                    SourceContentFormattingRequestedEvent(
                        sourceId = source.id,
                        userId = source.userId,
                        extractorId = provider.id
                    )
                )
            }

            logger.info(
                "[service] Successfully extracted content url={} provider={} aiFormatted={}",
                source.url.normalized,
                resolvedProvider,
                result.aiFormatted
            )
            return source
        } catch (e: Exception) {
            logger.error("[service] Failed to extract content url={}", source.url.normalized, e)
            if (source.status == SourceStatus.EXTRACTING) {
                source.failExtraction()
                sourceRepository.save(source)
            }
            throw ExtractionFailedException(source.url.normalized, e)
        }
    }

    private fun saveSharedSnapshot(source: Source, fetchedAt: Instant) {
        val content = source.content ?: return
        val metadata = source.metadata ?: return
        val sourceType = source.sourceType
        val url = source.url.normalized

        sharedSourceSnapshotRepository.markLatestAsNotLatest(url, Instant.now())

        val nextVersion = sharedSourceSnapshotRepository.findMaxVersionByUrlNormalized(url) + 1
        val snapshot = SharedSourceSnapshot(
            id = idGenerator.newId(),
            urlNormalized = url,
            sourceType = sourceType,
            status = SharedSourceSnapshotStatus.ACTIVE,
            content = content,
            metadata = metadata,
            fetchedAt = fetchedAt,
            expiresAt = freshnessPolicy.computeExpiresAt(sourceType, fetchedAt),
            version = nextVersion,
            isLatest = true
        )
        sharedSourceSnapshotRepository.save(snapshot)
        logger.info(
            "[service] Shared snapshot stored url={} snapshotId={} snapshotVersion={} sourceType={} fetchedAt={} expiresAt={}",
            url,
            snapshot.id,
            snapshot.version,
            snapshot.sourceType,
            snapshot.fetchedAt,
            snapshot.expiresAt
        )
    }

    private fun cacheAgeSeconds(from: Instant, to: Instant): Long {
        return Duration.between(from, to).seconds.coerceAtLeast(0)
    }

    private fun isReusableSnapshot(
        latestSnapshot: SharedSourceSnapshot?,
        now: Instant
    ): Boolean {
        return latestSnapshot != null &&
            latestSnapshot.status == SharedSourceSnapshotStatus.ACTIVE &&
            latestSnapshot.content != null &&
            latestSnapshot.metadata != null &&
            freshnessPolicy.isFresh(latestSnapshot.expiresAt, now)
    }

    private fun buildCacheHitResponse(
        command: CreateSourceCommand,
        userId: UUID,
        normalizedUrl: String,
        latestSnapshot: SharedSourceSnapshot?,
        snapshotCacheAge: Long?
    ): SourceResponse {
        requireNotNull(latestSnapshot)
        requireNotNull(latestSnapshot.content)
        requireNotNull(latestSnapshot.metadata)

        val source = Source(
            id = idGenerator.newId(),
            url = Url.from(command.url),
            status = SourceStatus.ACTIVE,
            content = latestSnapshot.content,
            metadata = latestSnapshot.metadata,
            sourceType = latestSnapshot.sourceType,
            userId = userId
        )
        sourceRepository.save(source)
        eventPublisher.publishEvent(
            SourceActivatedEvent(
                sourceId = source.id,
                userId = userId,
                activationReason = SourceActivationReason.CACHE_REUSE
            )
        )
        maybeRequestAsyncFormatting(source)

        val ttlSeconds = freshnessPolicy.ttlSeconds(latestSnapshot.sourceType)
        logger.info(
            "[service] Cache hit url={} snapshotId={} snapshotVersion={} sourceId={} cacheAgeSeconds={} freshnessTtlSeconds={}",
            normalizedUrl,
            latestSnapshot.id,
            latestSnapshot.version,
            source.id,
            snapshotCacheAge,
            ttlSeconds
        )

        return source.toResponse(
            reuseInfo = ReuseInfoDto(
                usedCache = true,
                cacheAgeSeconds = snapshotCacheAge,
                freshnessTtlSeconds = ttlSeconds
            )
        )
    }

    private fun maybeRequestAsyncFormatting(source: Source) {
        val metadata = source.metadata ?: return
        if (metadata.aiFormatted) {
            return
        }

        val provider = parseExtractionProvider(metadata.extractionProvider)
        if (provider == null) {
            logger.info(
                "[service] Skip formatting request sourceId={} reason=unknown_or_missing_provider provider={}",
                source.id,
                metadata.extractionProvider
            )
            return
        }

        eventPublisher.publishEvent(
            SourceContentFormattingRequestedEvent(
                sourceId = source.id,
                userId = source.userId,
                extractorId = provider
            )
        )
        logger.info(
            "[service] Published formatting request sourceId={} userId={} extractorId={}",
            source.id,
            source.userId,
            provider
        )
    }

    private fun parseExtractionProvider(rawProvider: String?): ExtractionProviderId? {
        if (rawProvider.isNullOrBlank()) {
            return null
        }
        return try {
            ExtractionProviderId.valueOf(rawProvider.trim().uppercase())
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
