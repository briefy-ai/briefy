package com.briefy.api.application.source

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@Component
class YouTubeExtractionWorker(
    private val sourceExtractionJobService: SourceExtractionJobService,
    private val sourceService: SourceService,
    @param:Value("\${extraction.youtube.enabled:true}")
    private val enabled: Boolean,
    @param:Value("\${extraction.youtube.worker.batch-size:5}")
    private val batchSize: Int,
    @param:Value("\${extraction.youtube.worker.lock-heartbeat-ms:30000}")
    private val lockHeartbeatMs: Long
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
            val heartbeatRunning = AtomicBoolean(true)
            val heartbeatThread = startLockHeartbeat(job.id, heartbeatRunning)
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
            } finally {
                heartbeatRunning.set(false)
                heartbeatThread.join(2_000)
            }
        }
    }

    private fun startLockHeartbeat(jobId: UUID, running: AtomicBoolean): Thread {
        return Thread(
            {
                while (running.get()) {
                    try {
                        Thread.sleep(lockHeartbeatMs.coerceAtLeast(1000L))
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return@Thread
                    }

                    if (!running.get()) {
                        return@Thread
                    }

                    val refreshed = runCatching {
                        sourceExtractionJobService.refreshProcessingLock(jobId, lockOwner, Instant.now())
                    }.getOrElse { error ->
                        logger.warn(
                            "[youtube-worker] lock_heartbeat_failed jobId={} lockOwner={}",
                            jobId,
                            lockOwner,
                            error
                        )
                        false
                    }

                    if (!refreshed) {
                        logger.warn(
                            "[youtube-worker] lock_heartbeat_skipped jobId={} reason=not_processing_or_lock_owner_changed",
                            jobId
                        )
                        return@Thread
                    }
                }
            },
            "youtube-lock-heartbeat-$jobId"
        ).apply {
            isDaemon = true
            start()
        }
    }
}
