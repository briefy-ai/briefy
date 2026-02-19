package com.briefy.api.application.briefing

import com.briefy.api.domain.knowledgegraph.briefing.BriefingGenerationJob
import com.briefy.api.domain.knowledgegraph.briefing.BriefingGenerationJobRepository
import com.briefy.api.domain.knowledgegraph.briefing.BriefingGenerationJobStatus
import com.briefy.api.infrastructure.id.IdGenerator
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class BriefingGenerationJobService(
    private val briefingGenerationJobRepository: BriefingGenerationJobRepository,
    private val idGenerator: IdGenerator
) {

    @Transactional
    fun enqueue(briefingId: UUID, userId: UUID, now: Instant): BriefingGenerationJob {
        val existing = briefingGenerationJobRepository.findByBriefingId(briefingId)
        if (existing != null) {
            existing.status = BriefingGenerationJobStatus.PENDING
            existing.attempts = 0
            existing.nextAttemptAt = now
            existing.lockOwner = null
            existing.lockedAt = null
            existing.lastError = null
            existing.updatedAt = now
            return briefingGenerationJobRepository.save(existing)
        }

        return briefingGenerationJobRepository.save(
            BriefingGenerationJob(
                id = idGenerator.newId(),
                briefingId = briefingId,
                userId = userId,
                status = BriefingGenerationJobStatus.PENDING,
                attempts = 0,
                maxAttempts = 1,
                nextAttemptAt = now,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    @Transactional
    fun claimDueJobs(now: Instant, batchSize: Int, lockOwner: String): List<BriefingGenerationJob> {
        val candidateIds = briefingGenerationJobRepository.findDueJobIds(
            statuses = listOf(BriefingGenerationJobStatus.PENDING),
            now = now,
            pageable = PageRequest.of(0, batchSize)
        )
        if (candidateIds.isEmpty()) return emptyList()

        return candidateIds.mapNotNull { jobId ->
            val claimed = briefingGenerationJobRepository.markAsProcessing(
                id = jobId,
                fromStatuses = listOf(BriefingGenerationJobStatus.PENDING),
                newStatus = BriefingGenerationJobStatus.PROCESSING,
                lockedAt = now,
                lockOwner = lockOwner,
                now = now
            ) > 0

            if (!claimed) {
                null
            } else {
                briefingGenerationJobRepository.findById(jobId).orElse(null)
            }
        }
    }

    @Transactional
    fun markSucceeded(jobId: UUID, now: Instant) {
        val job = briefingGenerationJobRepository.findById(jobId).orElse(null) ?: return
        job.status = BriefingGenerationJobStatus.SUCCEEDED
        job.lockOwner = null
        job.lockedAt = null
        job.lastError = null
        job.updatedAt = now
        briefingGenerationJobRepository.save(job)
    }

    @Transactional
    fun markFailed(jobId: UUID, error: String, now: Instant) {
        val job = briefingGenerationJobRepository.findById(jobId).orElse(null) ?: return
        job.status = BriefingGenerationJobStatus.FAILED
        job.attempts += 1
        job.lockOwner = null
        job.lockedAt = null
        job.lastError = error.take(MAX_ERROR_LENGTH)
        job.updatedAt = now
        briefingGenerationJobRepository.save(job)
    }

    companion object {
        private const val MAX_ERROR_LENGTH = 4000
    }
}
