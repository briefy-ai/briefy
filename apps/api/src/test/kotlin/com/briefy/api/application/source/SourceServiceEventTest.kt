package com.briefy.api.application.source

import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.SharedSourceSnapshotRepository
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.event.SourceArchivedEvent
import com.briefy.api.domain.knowledgegraph.source.event.SourceRestoredEvent
import com.briefy.api.infrastructure.extraction.ContentExtractor
import com.briefy.api.infrastructure.id.IdGenerator
import com.briefy.api.infrastructure.security.CurrentUserProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID

class SourceServiceEventTest {
    private val sourceRepository: SourceRepository = mock()
    private val sharedSourceSnapshotRepository: SharedSourceSnapshotRepository = mock()
    private val contentExtractor: ContentExtractor = mock()
    private val sourceTypeClassifier: SourceTypeClassifier = mock()
    private val freshnessPolicy: FreshnessPolicy = mock()
    private val currentUserProvider: CurrentUserProvider = mock()
    private val idGenerator: IdGenerator = mock()
    private val eventPublisher: ApplicationEventPublisher = mock()

    private val service = SourceService(
        sourceRepository = sourceRepository,
        sharedSourceSnapshotRepository = sharedSourceSnapshotRepository,
        contentExtractor = contentExtractor,
        sourceTypeClassifier = sourceTypeClassifier,
        freshnessPolicy = freshnessPolicy,
        currentUserProvider = currentUserProvider,
        idGenerator = idGenerator,
        eventPublisher = eventPublisher
    )

    @Test
    fun `deleteSource publishes SourceArchivedEvent when transitioning to archived`() {
        val userId = UUID.randomUUID()
        val source = createActiveSource(userId)
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)

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
    fun `deleteSource does not publish event when source already archived`() {
        val userId = UUID.randomUUID()
        val source = createActiveSource(userId).apply { archive() }
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.findByIdAndUserId(source.id, userId)).thenReturn(source)

        service.deleteSource(source.id)

        verify(sourceRepository, never()).save(any())
        verify(eventPublisher, never()).publishEvent(any<Any>())
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
