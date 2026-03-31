package com.briefy.api.application.briefing

import com.briefy.api.domain.knowledgegraph.briefing.BriefingGenerationJob
import com.briefy.api.domain.knowledgegraph.briefing.BriefingGenerationJobStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.MDC
import java.time.Instant
import java.util.UUID

class BriefingGenerationWorkerTest {

    private val briefingGenerationJobService = mock<BriefingGenerationJobService>()
    private val briefingGenerationService = mock<BriefingGenerationService>()

    @Test
    fun `pollAndProcess sets and clears userId MDC per job`() {
        val worker = BriefingGenerationWorker(
            briefingGenerationJobService = briefingGenerationJobService,
            briefingGenerationService = briefingGenerationService,
            enabled = true,
            batchSize = 5
        )
        val firstJob = job(UUID.randomUUID(), UUID.randomUUID())
        val secondJob = job(UUID.randomUUID(), UUID.randomUUID())

        whenever(briefingGenerationJobService.claimDueJobs(any(), eq(5), any())).thenReturn(listOf(firstJob, secondJob))
        doAnswer { invocation ->
            assertEquals(firstJob.userId.toString(), MDC.get("userId"))
            null
        }.whenever(briefingGenerationService).generateApprovedBriefing(firstJob.briefingId, firstJob.userId)
        doAnswer {
            assertEquals(secondJob.userId.toString(), MDC.get("userId"))
            null
        }.whenever(briefingGenerationService).generateApprovedBriefing(secondJob.briefingId, secondJob.userId)

        worker.pollAndProcess()

        verify(briefingGenerationService).generateApprovedBriefing(firstJob.briefingId, firstJob.userId)
        verify(briefingGenerationService).generateApprovedBriefing(secondJob.briefingId, secondJob.userId)
        verify(briefingGenerationJobService, times(2)).markSucceeded(any(), any())
        assertNull(MDC.get("userId"))
    }

    private fun job(briefingId: UUID, userId: UUID): BriefingGenerationJob {
        val now = Instant.now()
        return BriefingGenerationJob(
            id = UUID.randomUUID(),
            briefingId = briefingId,
            userId = userId,
            status = BriefingGenerationJobStatus.PENDING,
            attempts = 0,
            maxAttempts = 3,
            nextAttemptAt = now,
            createdAt = now,
            updatedAt = now
        )
    }
}
