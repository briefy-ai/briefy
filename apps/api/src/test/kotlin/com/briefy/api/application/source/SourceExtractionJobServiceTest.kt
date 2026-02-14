package com.briefy.api.application.source

import com.briefy.api.domain.knowledgegraph.source.SourceExtractionJobRepository
import com.briefy.api.domain.knowledgegraph.source.SourceExtractionJobStatus
import com.briefy.api.infrastructure.id.IdGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class SourceExtractionJobServiceTest {
    private val repository: SourceExtractionJobRepository = mock()
    private val idGenerator: IdGenerator = mock()
    private val service = SourceExtractionJobService(
        sourceExtractionJobRepository = repository,
        idGenerator = idGenerator,
        maxAttempts = 5,
        processingTimeoutSeconds = 900
    )

    @Test
    fun `reclaimStaleProcessingJobs moves stale processing jobs to retry`() {
        val now = Instant.parse("2026-02-14T10:00:00Z")
        whenever(
            repository.reclaimStaleProcessingJobs(
                processingStatus = SourceExtractionJobStatus.PROCESSING,
                retryStatus = SourceExtractionJobStatus.RETRY,
                staleBefore = Instant.parse("2026-02-14T09:45:00Z"),
                now = now
            )
        ).thenReturn(2)

        val reclaimed = service.reclaimStaleProcessingJobs(now)

        assertEquals(2, reclaimed)
        val staleBeforeCaptor = argumentCaptor<Instant>()
        verify(repository).reclaimStaleProcessingJobs(
            processingStatus = eq(SourceExtractionJobStatus.PROCESSING),
            retryStatus = eq(SourceExtractionJobStatus.RETRY),
            staleBefore = staleBeforeCaptor.capture(),
            now = eq(now)
        )
        assertEquals(Instant.parse("2026-02-14T09:45:00Z"), staleBeforeCaptor.firstValue)
    }

    @Test
    fun `enqueueYoutubeExtraction resets existing job to pending`() {
        val sourceId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val now = Instant.parse("2026-02-14T10:00:00Z")
        val existing = com.briefy.api.domain.knowledgegraph.source.SourceExtractionJob(
            id = UUID.randomUUID(),
            sourceId = sourceId,
            userId = userId,
            platform = "youtube",
            status = SourceExtractionJobStatus.PROCESSING,
            attempts = 3,
            maxAttempts = 5,
            nextAttemptAt = now.minusSeconds(60),
            lockedAt = now.minusSeconds(30),
            lockOwner = "worker-1",
            lastError = "boom",
            createdAt = now.minusSeconds(3600),
            updatedAt = now.minusSeconds(60)
        )
        whenever(repository.findBySourceId(sourceId)).thenReturn(existing)
        whenever(repository.save(existing)).thenReturn(existing)

        val result = service.enqueueYoutubeExtraction(sourceId, userId, now)

        assertEquals(SourceExtractionJobStatus.PENDING, result.status)
        assertEquals(0, result.attempts)
        assertEquals(now, result.nextAttemptAt)
        assertEquals(null, result.lockOwner)
        assertEquals(null, result.lockedAt)
        assertEquals(null, result.lastError)
    }
}
