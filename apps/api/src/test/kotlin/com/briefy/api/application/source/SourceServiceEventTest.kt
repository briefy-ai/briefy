package com.briefy.api.application.source

import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.SharedSourceSnapshotRepository
import com.briefy.api.domain.knowledgegraph.source.SharedSourceSnapshot
import com.briefy.api.domain.knowledgegraph.source.SharedSourceSnapshotStatus
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceType
import com.briefy.api.domain.knowledgegraph.source.event.SourceActivatedEvent
import com.briefy.api.domain.knowledgegraph.source.event.SourceActivationReason
import com.briefy.api.domain.knowledgegraph.source.event.SourceArchivedEvent
import com.briefy.api.domain.knowledgegraph.source.event.SourceContentFinalizedEvent
import com.briefy.api.domain.knowledgegraph.source.event.SourceContentFormattingRequestedEvent
import com.briefy.api.domain.knowledgegraph.source.event.SourceRestoredEvent
import com.briefy.api.domain.knowledgegraph.topic.TopicRepository
import com.briefy.api.domain.knowledgegraph.topic.Topic
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkRepository
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLink
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkStatus
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkTargetType
import com.briefy.api.infrastructure.extraction.ExtractionProvider
import com.briefy.api.infrastructure.extraction.ExtractionProviderId
import com.briefy.api.infrastructure.extraction.ExtractionProviderResolver
import com.briefy.api.infrastructure.extraction.ExtractionResult
import com.briefy.api.infrastructure.id.IdGenerator
import com.briefy.api.infrastructure.security.CurrentUserProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.UUID

class SourceServiceEventTest {
    private val sourceRepository: SourceRepository = mock()
    private val sharedSourceSnapshotRepository: SharedSourceSnapshotRepository = mock()
    private val sourceExtractionJobService: SourceExtractionJobService = mock()
    private val topicRepository: TopicRepository = mock()
    private val topicLinkRepository: TopicLinkRepository = mock()
    private val sourceDependencyChecker: SourceDependencyChecker = mock()
    private val extractionProviderResolver: ExtractionProviderResolver = mock()
    private val extractionProvider: ExtractionProvider = mock()
    private val sourceTypeClassifier: SourceTypeClassifier = mock()
    private val freshnessPolicy: FreshnessPolicy = mock()
    private val currentUserProvider: CurrentUserProvider = mock()
    private val idGenerator: IdGenerator = mock()
    private val eventPublisher: ApplicationEventPublisher = mock()

    init {
        whenever(extractionProvider.id).thenReturn(ExtractionProviderId.JSOUP)
    }

    private val service = SourceService(
        sourceRepository = sourceRepository,
        sharedSourceSnapshotRepository = sharedSourceSnapshotRepository,
        sourceExtractionJobService = sourceExtractionJobService,
        topicRepository = topicRepository,
        topicLinkRepository = topicLinkRepository,
        sourceDependencyChecker = sourceDependencyChecker,
        extractionProviderResolver = extractionProviderResolver,
        sourceTypeClassifier = sourceTypeClassifier,
        freshnessPolicy = freshnessPolicy,
        currentUserProvider = currentUserProvider,
        idGenerator = idGenerator,
        eventPublisher = eventPublisher
    )

    @Test
    fun `deleteSource hard deletes unreferenced source and publishes no archive event`() {
        val userId = UUID.randomUUID()
        val source = createActiveSource(userId)
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(sourceRepository.countByUrlNormalized(source.url.normalized)).thenReturn(1)
        whenever(sourceDependencyChecker.hasBlockingDependencies(source.id, userId)).thenReturn(false)

        service.deleteSource(source.id)

        verify(sourceRepository).delete(source)
        verify(eventPublisher, never()).publishEvent(any<Any>())
    }

    @Test
    fun `deleteSource archives source and publishes SourceArchivedEvent when dependencies exist`() {
        val userId = UUID.randomUUID()
        val source = createActiveSource(userId)
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(sourceDependencyChecker.hasBlockingDependencies(source.id, userId)).thenReturn(true)

        service.deleteSource(source.id)

        assertEquals(com.briefy.api.domain.knowledgegraph.source.SourceStatus.ARCHIVED, source.status)
        verify(sourceRepository).save(source)

        val eventCaptor = argumentCaptor<Any>()
        verify(eventPublisher).publishEvent(eventCaptor.capture())
        assertTrue(eventCaptor.firstValue is SourceArchivedEvent)
        val event = eventCaptor.firstValue as SourceArchivedEvent
        assertEquals(source.id, event.sourceId)
        assertEquals(userId, event.userId)
    }

    @Test
    fun `deleteSource is no-op when source not found`() {
        val userId = UUID.randomUUID()
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.findByIdAndUserId(UUID.fromString("00000000-0000-0000-0000-000000000000"), userId)).thenReturn(null)

        service.deleteSource(UUID.fromString("00000000-0000-0000-0000-000000000000"))

        verify(sourceRepository, never()).save(any())
        verify(sourceRepository, never()).delete(any())
        verify(eventPublisher, never()).publishEvent(any<Any>())
    }

    @Test
    fun `deleteSource removes source topic links, orphan topic, and snapshots for last url source`() {
        val userId = UUID.randomUUID()
        val source = createActiveSource(userId)
        val topic = Topic.activeUser(UUID.randomUUID(), userId, "security")
        val link = TopicLink.activeUserForSource(UUID.randomUUID(), topic.id, source.id, userId)
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(sourceDependencyChecker.hasBlockingDependencies(source.id, userId)).thenReturn(false)
        whenever(sourceRepository.countByUrlNormalized(source.url.normalized)).thenReturn(1)
        whenever(
            topicLinkRepository.findByUserIdAndTargetTypeAndTargetIdAndStatusIn(
                userId = userId,
                targetType = TopicLinkTargetType.SOURCE,
                targetId = source.id,
                statuses = listOf(TopicLinkStatus.SUGGESTED, TopicLinkStatus.ACTIVE)
            )
        ).thenReturn(listOf(link))
        whenever(topicRepository.findAllByIdInAndUserId(setOf(topic.id), userId)).thenReturn(listOf(topic))
        whenever(topicLinkRepository.countByUserIdAndTopicIdAndStatusIn(userId, topic.id, listOf(TopicLinkStatus.SUGGESTED, TopicLinkStatus.ACTIVE)))
            .thenReturn(0)

        service.deleteSource(source.id)

        verify(topicLinkRepository).deleteAll(listOf(link))
        verify(topicRepository).deleteAll(listOf(topic))
        verify(sourceRepository).delete(source)
        verify(sharedSourceSnapshotRepository).deleteByUrlNormalized(source.url.normalized)
    }

    @Test
    fun `deleteSource keeps snapshots and non-orphan topic when other url sources or links exist`() {
        val userId = UUID.randomUUID()
        val source = createActiveSource(userId)
        val topic = Topic.activeUser(UUID.randomUUID(), userId, "security")
        val link = TopicLink.activeUserForSource(UUID.randomUUID(), topic.id, source.id, userId)
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(sourceDependencyChecker.hasBlockingDependencies(source.id, userId)).thenReturn(false)
        whenever(sourceRepository.countByUrlNormalized(source.url.normalized)).thenReturn(2)
        whenever(
            topicLinkRepository.findByUserIdAndTargetTypeAndTargetIdAndStatusIn(
                userId = userId,
                targetType = TopicLinkTargetType.SOURCE,
                targetId = source.id,
                statuses = listOf(TopicLinkStatus.SUGGESTED, TopicLinkStatus.ACTIVE)
            )
        ).thenReturn(listOf(link))
        whenever(topicRepository.findAllByIdInAndUserId(setOf(topic.id), userId)).thenReturn(listOf(topic))
        whenever(topicLinkRepository.countByUserIdAndTopicIdAndStatusIn(userId, topic.id, listOf(TopicLinkStatus.SUGGESTED, TopicLinkStatus.ACTIVE)))
            .thenReturn(1)

        service.deleteSource(source.id)

        verify(topicRepository, never()).deleteAll(any<Iterable<Topic>>())
        verify(sharedSourceSnapshotRepository, never()).deleteByUrlNormalized(any())
    }

    @Test
    fun `restoreSource publishes SourceRestoredEvent when transitioning to active`() {
        val userId = UUID.randomUUID()
        val source = createActiveSource(userId).apply { archive() }
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)

        service.restoreSource(source.id)

        assertEquals(com.briefy.api.domain.knowledgegraph.source.SourceStatus.ACTIVE, source.status)
        verify(sourceRepository).save(source)

        val eventCaptor = argumentCaptor<Any>()
        verify(eventPublisher).publishEvent(eventCaptor.capture())
        assertTrue(eventCaptor.firstValue is SourceRestoredEvent)
        val event = eventCaptor.firstValue as SourceRestoredEvent
        assertEquals(source.id, event.sourceId)
        assertEquals(userId, event.userId)
    }

    @Test
    fun `restoreSource is idempotent for active source and publishes no event`() {
        val userId = UUID.randomUUID()
        val source = createActiveSource(userId)
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)

        service.restoreSource(source.id)

        verify(sourceRepository, never()).save(any())
        verify(eventPublisher, never()).publishEvent(any<Any>())
    }

    @Test
    fun `submitSource publishes SourceActivatedEvent with FRESH_EXTRACTION on fresh ingestion`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        val snapshotId = UUID.randomUUID()
        val url = "https://fresh-activation-test.com/article"
        val normalizedUrl = "https://fresh-activation-test.com/article"

        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.countByUrlNormalized(normalizedUrl)).thenReturn(0)
        whenever(sourceRepository.findByUserIdAndUrlNormalized(userId, normalizedUrl)).thenReturn(null)
        whenever(sourceTypeClassifier.classify(normalizedUrl)).thenReturn(SourceType.BLOG)
        whenever(freshnessPolicy.ttlSeconds(SourceType.BLOG)).thenReturn(7 * 24 * 60 * 60L)
        whenever(freshnessPolicy.computeExpiresAt(any(), any())).thenReturn(Instant.now().plusSeconds(7 * 24 * 60 * 60L))
        whenever(sharedSourceSnapshotRepository.findFirstByUrlNormalizedAndIsLatestTrue(normalizedUrl)).thenReturn(null)
        whenever(idGenerator.newId()).thenReturn(sourceId, snapshotId)
        whenever(sourceRepository.save(any())).thenAnswer { it.arguments[0] as Source }
        whenever(sharedSourceSnapshotRepository.findMaxVersionByUrlNormalized(normalizedUrl)).thenReturn(0)
        whenever(extractionProviderResolver.resolveProvider(userId, "web")).thenReturn(extractionProvider)
        whenever(extractionProvider.extract(normalizedUrl)).thenReturn(
            ExtractionResult(
                text = "Fresh extraction content",
                title = "Fresh Article",
                author = "Author",
                publishedDate = Instant.parse("2025-01-01T00:00:00Z")
            )
        )

        service.submitSource(CreateSourceCommand(url = url))

        val eventCaptor = argumentCaptor<Any>()
        verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture())
        val activatedEvent = eventCaptor.allValues.filterIsInstance<SourceActivatedEvent>().single()
        assertEquals(sourceId, activatedEvent.sourceId)
        assertEquals(userId, activatedEvent.userId)
        assertEquals(SourceActivationReason.FRESH_EXTRACTION, activatedEvent.activationReason)
        val formattingEvent = eventCaptor.allValues.filterIsInstance<SourceContentFormattingRequestedEvent>().single()
        assertEquals(sourceId, formattingEvent.sourceId)
        assertEquals(userId, formattingEvent.userId)
    }

    @Test
    fun `submitSource publishes SourceActivatedEvent with CACHE_REUSE on cache hit`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        val url = "https://cache-hit-activation-test.com/article"
        val normalizedUrl = "https://cache-hit-activation-test.com/article"
        val now = Instant.now()
        val snapshot = SharedSourceSnapshot(
            id = UUID.randomUUID(),
            urlNormalized = normalizedUrl,
            sourceType = SourceType.BLOG,
            status = SharedSourceSnapshotStatus.ACTIVE,
            content = Content.from("Cached article content"),
            metadata = Metadata(title = "Cached", aiFormatted = true, extractionProvider = "jsoup"),
            fetchedAt = now.minusSeconds(120),
            expiresAt = now.plusSeconds(3600),
            version = 1,
            isLatest = true
        )

        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.countByUrlNormalized(normalizedUrl)).thenReturn(1)
        whenever(sourceRepository.findByUserIdAndUrlNormalized(userId, normalizedUrl)).thenReturn(null)
        whenever(sourceTypeClassifier.classify(normalizedUrl)).thenReturn(SourceType.BLOG)
        whenever(freshnessPolicy.ttlSeconds(SourceType.BLOG)).thenReturn(7 * 24 * 60 * 60L)
        whenever(sharedSourceSnapshotRepository.findFirstByUrlNormalizedAndIsLatestTrue(normalizedUrl)).thenReturn(snapshot)
        whenever(freshnessPolicy.isFresh(any(), any())).thenReturn(true)
        whenever(idGenerator.newId()).thenReturn(sourceId)
        whenever(sourceRepository.save(any())).thenAnswer { it.arguments[0] as Source }

        service.submitSource(CreateSourceCommand(url = url))

        val eventCaptor = argumentCaptor<Any>()
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture())
        val activatedEvent = eventCaptor.allValues.filterIsInstance<SourceActivatedEvent>().single()
        assertEquals(sourceId, activatedEvent.sourceId)
        assertEquals(userId, activatedEvent.userId)
        assertEquals(SourceActivationReason.CACHE_REUSE, activatedEvent.activationReason)
        val finalizedEvent = eventCaptor.allValues.filterIsInstance<SourceContentFinalizedEvent>().single()
        assertEquals(sourceId, finalizedEvent.sourceId)
        assertEquals(userId, finalizedEvent.userId)
        assertTrue(eventCaptor.allValues.none { it is SourceContentFormattingRequestedEvent })
    }

    @Test
    fun `submitSource publishes SourceContentFinalizedEvent when extractor output is already formatted`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        val snapshotId = UUID.randomUUID()
        val url = "https://fresh-already-formatted-test.com/article"
        val normalizedUrl = "https://fresh-already-formatted-test.com/article"

        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.countByUrlNormalized(normalizedUrl)).thenReturn(0)
        whenever(sourceRepository.findByUserIdAndUrlNormalized(userId, normalizedUrl)).thenReturn(null)
        whenever(sourceTypeClassifier.classify(normalizedUrl)).thenReturn(SourceType.BLOG)
        whenever(freshnessPolicy.ttlSeconds(SourceType.BLOG)).thenReturn(7 * 24 * 60 * 60L)
        whenever(freshnessPolicy.computeExpiresAt(any(), any())).thenReturn(Instant.now().plusSeconds(7 * 24 * 60 * 60L))
        whenever(sharedSourceSnapshotRepository.findFirstByUrlNormalizedAndIsLatestTrue(normalizedUrl)).thenReturn(null)
        whenever(idGenerator.newId()).thenReturn(sourceId, snapshotId)
        whenever(sourceRepository.save(any())).thenAnswer { it.arguments[0] as Source }
        whenever(sharedSourceSnapshotRepository.findMaxVersionByUrlNormalized(normalizedUrl)).thenReturn(0)
        whenever(extractionProviderResolver.resolveProvider(userId, "web")).thenReturn(extractionProvider)
        whenever(extractionProvider.extract(normalizedUrl)).thenReturn(
            ExtractionResult(
                text = "Already formatted content",
                title = "Formatted Article",
                author = "Author",
                publishedDate = Instant.parse("2025-01-01T00:00:00Z"),
                aiFormatted = true
            )
        )

        service.submitSource(CreateSourceCommand(url = url))

        val eventCaptor = argumentCaptor<Any>()
        verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture())
        val activatedEvent = eventCaptor.allValues.filterIsInstance<SourceActivatedEvent>().single()
        assertEquals(sourceId, activatedEvent.sourceId)
        assertEquals(userId, activatedEvent.userId)
        val finalizedEvent = eventCaptor.allValues.filterIsInstance<SourceContentFinalizedEvent>().single()
        assertEquals(sourceId, finalizedEvent.sourceId)
        assertEquals(userId, finalizedEvent.userId)
        assertTrue(eventCaptor.allValues.none { it is SourceContentFormattingRequestedEvent })
    }

    @Test
    fun `submitSource enqueues youtube job and does not run sync extraction`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        val url = "https://youtube.com/watch?v=dQw4w9WgXcQ"
        val normalizedUrl = "https://youtube.com/watch?v=dQw4w9WgXcQ"

        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.countByUrlNormalized(normalizedUrl)).thenReturn(0)
        whenever(sourceRepository.findByUserIdAndUrlNormalized(userId, normalizedUrl)).thenReturn(null)
        whenever(sourceTypeClassifier.classify(normalizedUrl)).thenReturn(SourceType.VIDEO)
        whenever(freshnessPolicy.ttlSeconds(SourceType.VIDEO)).thenReturn(30 * 24 * 60 * 60L)
        whenever(sharedSourceSnapshotRepository.findFirstByUrlNormalizedAndIsLatestTrue(normalizedUrl)).thenReturn(null)
        whenever(idGenerator.newId()).thenReturn(sourceId)
        whenever(sourceRepository.save(any())).thenAnswer { it.arguments[0] as Source }

        val response = service.submitSource(CreateSourceCommand(url = url))

        assertEquals("submitted", response.status)
        val sourceIdCaptor = argumentCaptor<UUID>()
        val userIdCaptor = argumentCaptor<UUID>()
        verify(sourceExtractionJobService).enqueueYoutubeExtraction(sourceIdCaptor.capture(), userIdCaptor.capture(), any())
        assertEquals(sourceId, sourceIdCaptor.firstValue)
        assertEquals(userId, userIdCaptor.firstValue)
    }

    @Test
    fun `processQueuedExtraction retries failed source before extracting`() {
        val userId = UUID.randomUUID()
        val source = Source.create(
            id = UUID.randomUUID(),
            rawUrl = "https://youtube.com/watch?v=dQw4w9WgXcQ",
            userId = userId,
            sourceType = SourceType.VIDEO
        ).apply {
            startExtraction()
            failExtraction()
        }
        val snapshotId = UUID.randomUUID()

        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(sourceRepository.save(any())).thenAnswer { it.arguments[0] as Source }
        whenever(extractionProviderResolver.resolveProvider(userId, "youtube")).thenReturn(extractionProvider)
        whenever(extractionProvider.id).thenReturn(ExtractionProviderId.YOUTUBE)
        whenever(extractionProvider.extract(source.url.normalized)).thenReturn(
            ExtractionResult(
                text = "transcript text",
                title = "video title",
                author = "uploader",
                publishedDate = Instant.parse("2025-01-01T00:00:00Z"),
                aiFormatted = true,
                videoId = "dQw4w9WgXcQ",
                videoEmbedUrl = "https://www.youtube.com/embed/dQw4w9WgXcQ",
                videoDurationSeconds = 120
            )
        )
        whenever(idGenerator.newId()).thenReturn(snapshotId)
        whenever(sharedSourceSnapshotRepository.findMaxVersionByUrlNormalized(source.url.normalized)).thenReturn(0)
        whenever(freshnessPolicy.computeExpiresAt(any(), any())).thenReturn(Instant.now().plusSeconds(3600))

        val response = service.processQueuedExtraction(source.id, userId)

        assertEquals("active", response.status)
        assertEquals("dQw4w9WgXcQ", response.metadata?.videoId)
        verify(extractionProviderResolver).resolveProvider(userId, "youtube")
    }

    @Test
    fun `processQueuedExtraction publishes formatting request for youtube captions transcript`() {
        val userId = UUID.randomUUID()
        val source = Source.create(
            id = UUID.randomUUID(),
            rawUrl = "https://youtube.com/watch?v=dQw4w9WgXcQ",
            userId = userId,
            sourceType = SourceType.VIDEO
        )
        val snapshotId = UUID.randomUUID()

        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(sourceRepository.save(any())).thenAnswer { it.arguments[0] as Source }
        whenever(extractionProviderResolver.resolveProvider(userId, "youtube")).thenReturn(extractionProvider)
        whenever(extractionProvider.id).thenReturn(ExtractionProviderId.YOUTUBE)
        whenever(extractionProvider.extract(source.url.normalized)).thenReturn(
            ExtractionResult(
                text = "caption transcript",
                title = "video title",
                author = "uploader",
                publishedDate = Instant.parse("2025-01-01T00:00:00Z"),
                aiFormatted = false,
                videoId = "dQw4w9WgXcQ",
                videoEmbedUrl = "https://www.youtube.com/embed/dQw4w9WgXcQ",
                videoDurationSeconds = 120,
                transcriptSource = "captions"
            )
        )
        whenever(idGenerator.newId()).thenReturn(snapshotId)
        whenever(sharedSourceSnapshotRepository.findMaxVersionByUrlNormalized(source.url.normalized)).thenReturn(0)
        whenever(freshnessPolicy.computeExpiresAt(any(), any())).thenReturn(Instant.now().plusSeconds(3600))

        service.processQueuedExtraction(source.id, userId)

        val eventCaptor = argumentCaptor<Any>()
        verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture())
        val formattingEvents = eventCaptor.allValues.filterIsInstance<SourceContentFormattingRequestedEvent>()
        assertEquals(1, formattingEvents.size)
        assertEquals(source.id, formattingEvents.first().sourceId)
        assertEquals(ExtractionProviderId.YOUTUBE, formattingEvents.first().extractorId)
    }

    @Test
    fun `processQueuedExtraction does not publish formatting request for youtube whisper transcript`() {
        val userId = UUID.randomUUID()
        val source = Source.create(
            id = UUID.randomUUID(),
            rawUrl = "https://youtube.com/watch?v=dQw4w9WgXcQ",
            userId = userId,
            sourceType = SourceType.VIDEO
        )
        val snapshotId = UUID.randomUUID()

        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)
        whenever(sourceRepository.save(any())).thenAnswer { it.arguments[0] as Source }
        whenever(extractionProviderResolver.resolveProvider(userId, "youtube")).thenReturn(extractionProvider)
        whenever(extractionProvider.id).thenReturn(ExtractionProviderId.YOUTUBE)
        whenever(extractionProvider.extract(source.url.normalized)).thenReturn(
            ExtractionResult(
                text = "whisper transcript",
                title = "video title",
                author = "uploader",
                publishedDate = Instant.parse("2025-01-01T00:00:00Z"),
                aiFormatted = true,
                videoId = "dQw4w9WgXcQ",
                videoEmbedUrl = "https://www.youtube.com/embed/dQw4w9WgXcQ",
                videoDurationSeconds = 120,
                transcriptSource = "whisper"
            )
        )
        whenever(idGenerator.newId()).thenReturn(snapshotId)
        whenever(sharedSourceSnapshotRepository.findMaxVersionByUrlNormalized(source.url.normalized)).thenReturn(0)
        whenever(freshnessPolicy.computeExpiresAt(any(), any())).thenReturn(Instant.now().plusSeconds(3600))

        service.processQueuedExtraction(source.id, userId)

        val eventCaptor = argumentCaptor<Any>()
        verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture())
        val formattingEvents = eventCaptor.allValues.filterIsInstance<SourceContentFormattingRequestedEvent>()
        assertTrue(formattingEvents.isEmpty())
    }

    private fun createActiveSource(userId: UUID): Source {
        return Source.create(
            id = UUID.randomUUID(),
            rawUrl = "https://example.com/article",
            userId = userId
        ).apply {
            startExtraction()
            completeExtraction(Content.from("example source content"), Metadata())
        }
    }
}
