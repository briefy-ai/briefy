package com.briefy.api.application.briefing

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class BriefingGenerationWorker(
    private val briefingGenerationJobService: BriefingGenerationJobService,
    private val briefingGenerationService: BriefingGenerationService,
    @param:Value("\${briefing.generation.enabled:true}")
    private val enabled: Boolean,
    @param:Value("\${briefing.generation.worker.batch-size:5}")
    private val batchSize: Int
) {
    private val logger = LoggerFactory.getLogger(BriefingGenerationWorker::class.java)
    private val lockOwner: String = "briefing-worker-${UUID.randomUUID()}"

    @Scheduled(fixedDelayString = "\${briefing.generation.worker.poll-ms:5000}")
    fun pollAndProcess() {
        if (!enabled) {
            return
        }

        val jobs = briefingGenerationJobService.claimDueJobs(
            now = Instant.now(),
            batchSize = batchSize.coerceAtLeast(1),
            lockOwner = lockOwner
        )
        if (jobs.isEmpty()) {
            return
        }

        jobs.forEach { job ->
            try {
                briefingGenerationService.generateApprovedBriefing(job.briefingId, job.userId)
                briefingGenerationJobService.markSucceeded(job.id, Instant.now())
            } catch (ex: Exception) {
                logger.warn(
                    "[briefing-worker] generation_failed jobId={} briefingId={} userId={}",
                    job.id,
                    job.briefingId,
                    job.userId,
                    ex
                )
                briefingGenerationJobService.markFailed(job.id, ex.message ?: "generation_failed", Instant.now())
            }
        }
    }
}
