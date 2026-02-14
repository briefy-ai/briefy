package com.briefy.api.application.telegram

import com.briefy.api.config.TelegramProperties
import com.briefy.api.domain.conversational.telegram.TelegramIngestionJob
import com.briefy.api.domain.conversational.telegram.TelegramIngestionJobRepository
import com.briefy.api.domain.conversational.telegram.TelegramIngestionJobStatus
import com.briefy.api.infrastructure.id.IdGenerator
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class TelegramIngestionJobService(
    private val telegramIngestionJobRepository: TelegramIngestionJobRepository,
    private val idGenerator: IdGenerator,
    private val telegramProperties: TelegramProperties
) {
    @Transactional
    fun enqueue(
        telegramChatId: Long,
        telegramMessageId: Long,
        telegramUserId: Long,
        linkedUserId: UUID,
        payloadText: String,
        now: Instant
    ): TelegramIngestionJob {
        val existing = telegramIngestionJobRepository.findByTelegramChatIdAndTelegramMessageId(
            telegramChatId = telegramChatId,
            telegramMessageId = telegramMessageId
        )
        if (existing != null) {
            return existing
        }

        return telegramIngestionJobRepository.save(
            TelegramIngestionJob(
                id = idGenerator.newId(),
                telegramChatId = telegramChatId,
                telegramMessageId = telegramMessageId,
                telegramUserId = telegramUserId,
                linkedUserId = linkedUserId,
                payloadText = payloadText,
                status = TelegramIngestionJobStatus.PENDING,
                attempts = 0,
                maxAttempts = telegramProperties.ingestion.worker.maxAttempts,
                nextAttemptAt = now,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    @Transactional
    fun claimDueJobs(now: Instant, batchSize: Int, lockOwner: String): List<TelegramIngestionJob> {
        val candidateIds = telegramIngestionJobRepository.findDueJobIds(
            statuses = listOf(TelegramIngestionJobStatus.PENDING, TelegramIngestionJobStatus.RETRY),
            now = now,
            pageable = PageRequest.of(0, batchSize)
        )
        if (candidateIds.isEmpty()) return emptyList()

        return candidateIds.mapNotNull { id ->
            val claimed = telegramIngestionJobRepository.markAsProcessing(
                id = id,
                fromStatuses = listOf(TelegramIngestionJobStatus.PENDING, TelegramIngestionJobStatus.RETRY),
                newStatus = TelegramIngestionJobStatus.PROCESSING,
                lockedAt = now,
                lockOwner = lockOwner,
                now = now
            ) > 0
            if (!claimed) {
                null
            } else {
                telegramIngestionJobRepository.findById(id).orElse(null)
            }
        }
    }

    @Transactional
    fun markSucceeded(jobId: UUID, now: Instant) {
        val job = telegramIngestionJobRepository.findById(jobId).orElse(null) ?: return
        job.status = TelegramIngestionJobStatus.SUCCEEDED
        job.lockOwner = null
        job.lockedAt = null
        job.lastError = null
        job.updatedAt = now
        telegramIngestionJobRepository.save(job)
    }

    @Transactional
    fun markRetry(jobId: UUID, error: String, now: Instant) {
        val job = telegramIngestionJobRepository.findById(jobId).orElse(null) ?: return
        job.attempts += 1
        job.status = if (job.attempts >= job.maxAttempts) {
            TelegramIngestionJobStatus.FAILED
        } else {
            TelegramIngestionJobStatus.RETRY
        }
        job.nextAttemptAt = now.plusSeconds(nextBackoffSeconds(job.attempts))
        job.lockOwner = null
        job.lockedAt = null
        job.lastError = error.take(MAX_ERROR_LENGTH)
        job.updatedAt = now
        telegramIngestionJobRepository.save(job)
    }

    private fun nextBackoffSeconds(attempt: Int): Long {
        return when (attempt) {
            1 -> 5L
            2 -> 30L
            else -> 120L
        }
    }

    companion object {
        private const val MAX_ERROR_LENGTH = 4000
    }
}
