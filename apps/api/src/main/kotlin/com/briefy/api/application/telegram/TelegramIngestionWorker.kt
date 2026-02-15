package com.briefy.api.application.telegram

import com.briefy.api.config.TelegramProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class TelegramIngestionWorker(
    private val telegramProperties: TelegramProperties,
    private val telegramIngestionJobService: TelegramIngestionJobService,
    private val telegramIngestionProcessor: TelegramIngestionProcessor
) {
    private val logger = LoggerFactory.getLogger(TelegramIngestionWorker::class.java)
    private val lockOwner = "telegram-ingestion-${UUID.randomUUID()}"

    @Scheduled(fixedDelayString = "\${telegram.ingestion.worker.poll-ms:5000}")
    fun pollAndProcess() {
        if (!telegramProperties.integration.enabled) return

        val now = Instant.now()
        val jobs = telegramIngestionJobService.claimDueJobs(
            now = now,
            batchSize = telegramProperties.ingestion.worker.batchSize.coerceAtLeast(1),
            lockOwner = lockOwner
        )
        if (jobs.isEmpty()) return

        jobs.forEach { job ->
            try {
                telegramIngestionProcessor.process(job)
                telegramIngestionJobService.markSucceeded(job.id, Instant.now())
            } catch (e: Exception) {
                logger.warn(
                    "[telegram] ingestion job failed jobId={} telegramUserId={} attempt={}",
                    job.id,
                    job.telegramUserId,
                    job.attempts + 1,
                    e
                )
                telegramIngestionJobService.markRetry(job.id, e.message ?: "unknown_error", Instant.now())
            }
        }
    }
}
