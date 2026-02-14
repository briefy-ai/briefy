package com.briefy.api.application.source

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class YouTubeExtractionWorker(
    private val sourceExtractionJobService: SourceExtractionJobService,
    private val sourceService: SourceService,
    @param:Value("\${extraction.youtube.enabled:true}")
    private val enabled: Boolean,
    @param:Value("\${extraction.youtube.worker.batch-size:5}")
    private val batchSize: Int
) {
    private val logger = LoggerFactory.getLogger(YouTubeExtractionWorker::class.java)
    private val lockOwner: String = "youtube-worker-${UUID.randomUUID()}"

    @Scheduled(fixedDelayString = "\${extraction.youtube.worker.poll-ms:5000}")
    fun pollAndProcess() {
        if (!enabled) return

        val now = Instant.now()
        val reclaimed = sourceExtractionJobService.reclaimStaleProcessingJobs(now)
        if (reclaimed > 0) {
            logger.info("[youtube-worker] reclaimed_stale_processing_jobs count={}", reclaimed)
        }

        val jobs = sourceExtractionJobService.claimDueJobs(now, batchSize.coerceAtLeast(1), lockOwner)
        if (jobs.isEmpty()) return

        jobs.forEach { job ->
            try {
                sourceService.processQueuedExtraction(job.sourceId, job.userId)
                sourceExtractionJobService.markSucceeded(job.id, Instant.now())
            } catch (e: Exception) {
                logger.warn(
                    "[youtube-worker] extraction_failed jobId={} sourceId={} userId={} attempt={}",
                    job.id,
                    job.sourceId,
                    job.userId,
                    job.attempts + 1,
                    e
                )
                sourceExtractionJobService.markRetry(job.id, e.message ?: "unknown_error", Instant.now())
            }
        }
    }
}
