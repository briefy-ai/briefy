package com.briefy.api.application.source

import com.briefy.api.domain.knowledgegraph.source.SourceExtractionJob
import com.briefy.api.domain.knowledgegraph.source.SourceExtractionJobRepository
import com.briefy.api.domain.knowledgegraph.source.SourceExtractionJobStatus
import com.briefy.api.infrastructure.id.IdGenerator
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class SourceExtractionJobService(
    private val sourceExtractionJobRepository: SourceExtractionJobRepository,
    private val idGenerator: IdGenerator,
    @param:Value("\${extraction.youtube.worker.max-attempts:5}")
    private val maxAttempts: Int,
    @param:Value("\${extraction.youtube.worker.processing-timeout-seconds:900}")
    private val processingTimeoutSeconds: Long
) {
    @Transactional
    fun enqueueYoutubeExtraction(sourceId: UUID, userId: UUID, now: Instant): SourceExtractionJob {
        val existing = sourceExtractionJobRepository.findBySourceId(sourceId)
        if (existing != null) {
            existing.status = SourceExtractionJobStatus.PENDING
            existing.attempts = 0
            existing.maxAttempts = maxAttempts
            existing.nextAttemptAt = now
            existing.lockedAt = null
            existing.lockOwner = null
            existing.lastError = null
            existing.updatedAt = now
            return sourceExtractionJobRepository.save(existing)
        }

        return sourceExtractionJobRepository.save(
            SourceExtractionJob(
                id = idGenerator.newId(),
                sourceId = sourceId,
                userId = userId,
                platform = "youtube",
                status = SourceExtractionJobStatus.PENDING,
                attempts = 0,
                maxAttempts = maxAttempts,
                nextAttemptAt = now,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    @Transactional
    fun claimDueJobs(now: Instant, batchSize: Int, lockOwner: String): List<SourceExtractionJob> {
        val candidateIds = sourceExtractionJobRepository.findDueJobIds(
            statuses = listOf(SourceExtractionJobStatus.PENDING, SourceExtractionJobStatus.RETRY),
            now = now,
            pageable = PageRequest.of(0, batchSize)
        )
        if (candidateIds.isEmpty()) return emptyList()

        return candidateIds.mapNotNull { id ->
            val claimed = sourceExtractionJobRepository.markAsProcessing(
                id = id,
                fromStatuses = listOf(SourceExtractionJobStatus.PENDING, SourceExtractionJobStatus.RETRY),
                newStatus = SourceExtractionJobStatus.PROCESSING,
                lockedAt = now,
                lockOwner = lockOwner,
                now = now
            ) > 0
            if (!claimed) {
                null
            } else {
                sourceExtractionJobRepository.findById(id).orElse(null)
            }
        }
    }

    @Transactional
    fun reclaimStaleProcessingJobs(now: Instant): Int {
        val staleBefore = now.minusSeconds(processingTimeoutSeconds.coerceAtLeast(1))
        return sourceExtractionJobRepository.reclaimStaleProcessingJobs(
            processingStatus = SourceExtractionJobStatus.PROCESSING,
            retryStatus = SourceExtractionJobStatus.RETRY,
            staleBefore = staleBefore,
            now = now
        )
    }

    @Transactional
    fun refreshProcessingLock(jobId: UUID, lockOwner: String, now: Instant): Boolean {
        return sourceExtractionJobRepository.refreshProcessingLock(
            id = jobId,
            processingStatus = SourceExtractionJobStatus.PROCESSING,
            lockOwner = lockOwner,
            now = now
        ) > 0
    }

    @Transactional
    fun markSucceeded(jobId: UUID, now: Instant) {
        val job = sourceExtractionJobRepository.findById(jobId).orElse(null) ?: return
        job.status = SourceExtractionJobStatus.SUCCEEDED
        job.lockOwner = null
        job.lockedAt = null
        job.lastError = null
        job.updatedAt = now
        sourceExtractionJobRepository.save(job)
    }

    @Transactional
    fun markRetry(jobId: UUID, error: String, now: Instant) {
        val job = sourceExtractionJobRepository.findById(jobId).orElse(null) ?: return
        job.attempts += 1
        job.status = if (job.attempts >= job.maxAttempts) {
            SourceExtractionJobStatus.FAILED
        } else {
            SourceExtractionJobStatus.RETRY
        }
        job.nextAttemptAt = now.plusSeconds(nextBackoffSeconds(job.attempts))
        job.lockOwner = null
        job.lockedAt = null
        job.lastError = error.take(4000)
        job.updatedAt = now
        sourceExtractionJobRepository.save(job)
    }

    private fun nextBackoffSeconds(attempt: Int): Long {
        return when (attempt) {
            1 -> 60L
            2 -> 5 * 60L
            3 -> 15 * 60L
            4 -> 60 * 60L
            else -> 6 * 60 * 60L
        }
    }
}
