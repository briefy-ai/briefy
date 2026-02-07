package com.briefy.api.application.source

import com.briefy.api.domain.knowledgegraph.source.*
import com.briefy.api.infrastructure.extraction.ContentExtractor
import com.briefy.api.infrastructure.id.IdGenerator
import com.briefy.api.infrastructure.security.CurrentUserProvider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class SourceService(
    private val sourceRepository: SourceRepository,
    private val sharedSourceSnapshotRepository: SharedSourceSnapshotRepository,
    private val contentExtractor: ContentExtractor,
    private val sourceTypeClassifier: SourceTypeClassifier,
    private val freshnessPolicy: FreshnessPolicy,
    private val currentUserProvider: CurrentUserProvider,
    private val idGenerator: IdGenerator
) {
    private val logger = LoggerFactory.getLogger(SourceService::class.java)

    @Transactional
    fun submitSource(command: CreateSourceCommand): SourceResponse {
        val userId = currentUserProvider.requireUserId()
        val normalizedUrl = Url.normalize(command.url)
        val sharedUrlSourceCount = sourceRepository.countByUrlNormalized(normalizedUrl)

        // Check for duplicate
        sourceRepository.findByUserIdAndUrlNormalized(userId, normalizedUrl)?.let {
            throw SourceAlreadyExistsException(normalizedUrl)
        }

        val sourceType = sourceTypeClassifier.classify(normalizedUrl)
        val freshnessTtlSeconds = freshnessPolicy.ttlSeconds(sourceType)
        val now = Instant.now()
        val latestSnapshot = sharedSourceSnapshotRepository.findFirstByUrlNormalizedAndIsLatestTrue(normalizedUrl)
        val snapshotCacheAge = latestSnapshot?.let { cacheAgeSeconds(it.fetchedAt, now) }

        logger.info(
            "Source submit received url={} userId={} sourceType={} sharedUrlSourceCount={}",
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
            logger.info("Cache miss url={} reason=no_snapshot fetching_fresh=true", normalizedUrl)
        } else {
            logger.info(
                "Cache miss url={} reason=stale_or_invalid snapshotId={} snapshotVersion={} snapshotStatus={} expiresAt={} now={} fetching_fresh=true",
                normalizedUrl,
                latestSnapshot.id,
                latestSnapshot.version,
                latestSnapshot.status,
                latestSnapshot.expiresAt,
                now
            )
        }

        // Create source and extract fresh content
        val source = Source.create(idGenerator.newId(), command.url, userId, sourceType)
        sourceRepository.save(source)
        val extractedSource = extractContent(source)
        saveSharedSnapshot(extractedSource, Instant.now())

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
        val sources = if (status != null) {
            sourceRepository.findByUserIdAndStatus(userId, status)
        } else {
            sourceRepository.findByUserId(userId)
        }
        return sources.map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun getSource(id: UUID): SourceResponse {
        val userId = currentUserProvider.requireUserId()
        val source = sourceRepository.findByIdAndUserId(id, userId)
            ?: throw SourceNotFoundException(id)
        return source.toResponse()
    }

    @Transactional
    fun retryExtraction(id: UUID): SourceResponse {
        val userId = currentUserProvider.requireUserId()
        val source = sourceRepository.findByIdAndUserId(id, userId)
            ?: throw SourceNotFoundException(id)

        if (source.status != SourceStatus.FAILED) {
            throw InvalidSourceStateException("Can only retry extraction for failed sources. Current status: ${source.status}")
        }

        source.retry()
        sourceRepository.save(source)

        return extractContent(source).toResponse()
    }

    private fun extractContent(source: Source): Source {
        source.startExtraction()
        sourceRepository.save(source)

        try {
            val result = contentExtractor.extract(source.url.normalized)

            val content = Content.from(result.text)
            val metadata = Metadata.from(
                title = result.title,
                author = result.author,
                publishedDate = result.publishedDate,
                platform = source.url.platform,
                wordCount = content.wordCount
            )

            source.completeExtraction(content, metadata)
            sourceRepository.save(source)

            logger.info("Successfully extracted content from ${source.url.normalized}")
            return source
        } catch (e: Exception) {
            logger.error("Failed to extract content from ${source.url.normalized}", e)
            source.failExtraction()
            sourceRepository.save(source)
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
            "Shared snapshot stored url={} snapshotId={} snapshotVersion={} sourceType={} fetchedAt={} expiresAt={}",
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

        val ttlSeconds = freshnessPolicy.ttlSeconds(latestSnapshot.sourceType)
        logger.info(
            "Cache hit url={} snapshotId={} snapshotVersion={} sourceId={} cacheAgeSeconds={} freshnessTtlSeconds={}",
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
}
