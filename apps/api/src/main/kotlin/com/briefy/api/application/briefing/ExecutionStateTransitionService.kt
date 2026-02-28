package com.briefy.api.application.briefing

import com.briefy.api.domain.knowledgegraph.briefing.*
import com.briefy.api.infrastructure.id.IdGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class ExecutionStateTransitionService(
    private val briefingRunRepository: BriefingRunRepository,
    private val subagentRunRepository: SubagentRunRepository,
    private val synthesisRunRepository: SynthesisRunRepository,
    private val runEventRepository: RunEventRepository,
    private val idGenerator: IdGenerator,
    private val objectMapper: ObjectMapper
) {

    @Transactional
    fun startBriefingRun(runId: UUID, eventId: UUID, occurredAt: Instant = Instant.now()): BriefingRun {
        val run = requireBriefingRun(runId)
        if (isDuplicateEvent(eventId, run.id, null)) {
            return run
        }
        ensureBriefingTransition(run, BriefingRunStatus.RUNNING)

        run.transitionTo(BriefingRunStatus.RUNNING)
        run.startedAt = run.startedAt ?: occurredAt
        run.failureCode = null
        run.failureMessage = null
        run.updatedAt = occurredAt
        val saved = briefingRunRepository.save(run)

        recordEvent(
            eventId = eventId,
            briefingRunId = run.id,
            subagentRunId = null,
            eventType = EVENT_BRIEFING_RUN_STARTED,
            occurredAt = occurredAt,
            payloadJson = payload("status" to saved.status.dbValue)
        )
        return saved
    }

    @Transactional
    fun requestBriefingRunCancellation(
        runId: UUID,
        eventId: UUID,
        occurredAt: Instant = Instant.now()
    ): BriefingRun {
        val run = requireBriefingRun(runId)
        if (isDuplicateEvent(eventId, run.id, null)) {
            return run
        }
        ensureBriefingTransition(run, BriefingRunStatus.CANCELLING)

        run.transitionTo(BriefingRunStatus.CANCELLING)
        run.cancelRequestedAt = run.cancelRequestedAt ?: occurredAt
        run.updatedAt = occurredAt
        val saved = briefingRunRepository.save(run)

        recordEvent(
            eventId = eventId,
            briefingRunId = run.id,
            subagentRunId = null,
            eventType = EVENT_BRIEFING_RUN_CANCEL_REQUESTED,
            occurredAt = occurredAt,
            payloadJson = payload("status" to saved.status.dbValue)
        )
        return saved
    }

    @Transactional
    fun markBriefingRunTimedOut(
        runId: UUID,
        eventId: UUID,
        failureMessage: String? = null,
        occurredAt: Instant = Instant.now()
    ): BriefingRun {
        val run = requireBriefingRun(runId)
        if (isDuplicateEvent(eventId, run.id, null)) {
            return run
        }
        ensureBriefingTransition(run, BriefingRunStatus.FAILED)

        run.transitionTo(BriefingRunStatus.FAILED)
        run.failureCode = BriefingRunFailureCode.GLOBAL_TIMEOUT
        run.failureMessage = failureMessage?.take(MAX_FAILURE_MESSAGE)
        run.endedAt = occurredAt
        run.updatedAt = occurredAt
        val saved = briefingRunRepository.save(run)

        recordEvent(
            eventId = eventId,
            briefingRunId = run.id,
            subagentRunId = null,
            eventType = EVENT_BRIEFING_RUN_TIMED_OUT,
            occurredAt = occurredAt,
            payloadJson = payload(
                "status" to saved.status.dbValue,
                "failureCode" to saved.failureCode?.dbValue
            )
        )
        return saved
    }

    @Transactional
    fun markBriefingRunFailed(
        runId: UUID,
        eventId: UUID,
        failureCode: BriefingRunFailureCode,
        failureMessage: String? = null,
        occurredAt: Instant = Instant.now()
    ): BriefingRun {
        val run = requireBriefingRun(runId)
        if (isDuplicateEvent(eventId, run.id, null)) {
            return run
        }
        ensureBriefingTransition(run, BriefingRunStatus.FAILED)

        run.transitionTo(BriefingRunStatus.FAILED)
        run.failureCode = failureCode
        run.failureMessage = failureMessage?.take(MAX_FAILURE_MESSAGE)
        run.endedAt = occurredAt
        run.updatedAt = occurredAt
        val saved = briefingRunRepository.save(run)

        recordEvent(
            eventId = eventId,
            briefingRunId = run.id,
            subagentRunId = null,
            eventType = EVENT_BRIEFING_RUN_FAILED,
            occurredAt = occurredAt,
            payloadJson = payload(
                "status" to saved.status.dbValue,
                "failureCode" to saved.failureCode?.dbValue
            )
        )
        return saved
    }

    @Transactional
    fun markBriefingRunSucceeded(runId: UUID, eventId: UUID, occurredAt: Instant = Instant.now()): BriefingRun {
        val run = requireBriefingRun(runId)
        if (isDuplicateEvent(eventId, run.id, null)) {
            return run
        }
        ensureBriefingTransition(run, BriefingRunStatus.SUCCEEDED)

        run.transitionTo(BriefingRunStatus.SUCCEEDED)
        run.failureCode = null
        run.failureMessage = null
        run.endedAt = occurredAt
        run.updatedAt = occurredAt
        val saved = briefingRunRepository.save(run)

        recordEvent(
            eventId = eventId,
            briefingRunId = run.id,
            subagentRunId = null,
            eventType = EVENT_BRIEFING_RUN_COMPLETED,
            occurredAt = occurredAt,
            payloadJson = payload("status" to saved.status.dbValue)
        )
        return saved
    }

    @Transactional
    fun markBriefingRunCancelled(runId: UUID, eventId: UUID, occurredAt: Instant = Instant.now()): BriefingRun {
        val run = requireBriefingRun(runId)
        if (isDuplicateEvent(eventId, run.id, null)) {
            return run
        }
        ensureBriefingTransition(run, BriefingRunStatus.CANCELLED)

        run.transitionTo(BriefingRunStatus.CANCELLED)
        run.failureCode = BriefingRunFailureCode.CANCELLED
        run.failureMessage = null
        run.endedAt = occurredAt
        run.updatedAt = occurredAt
        val saved = briefingRunRepository.save(run)

        recordEvent(
            eventId = eventId,
            briefingRunId = run.id,
            subagentRunId = null,
            eventType = EVENT_BRIEFING_RUN_CANCELLED,
            occurredAt = occurredAt,
            payloadJson = payload("status" to saved.status.dbValue)
        )
        return saved
    }

    @Transactional
    fun dispatchSubagentRun(subagentRunId: UUID, eventId: UUID, occurredAt: Instant = Instant.now()): SubagentRun {
        val run = requireSubagentRun(subagentRunId)
        if (isDuplicateEvent(eventId, run.briefingRunId, run.id)) {
            return run
        }
        ensureSubagentTransition(run, SubagentRunStatus.RUNNING)

        run.transitionTo(SubagentRunStatus.RUNNING)
        run.startedAt = run.startedAt ?: occurredAt
        run.updatedAt = occurredAt
        val saved = subagentRunRepository.save(run)

        recordEvent(
            eventId = eventId,
            briefingRunId = run.briefingRunId,
            subagentRunId = run.id,
            eventType = EVENT_SUBAGENT_DISPATCHED,
            occurredAt = occurredAt,
            attempt = saved.attempt,
            payloadJson = payload(
                "status" to saved.status.dbValue,
                "personaKey" to saved.personaKey
            )
        )
        return saved
    }

    @Transactional
    fun markSubagentCompletedNonEmpty(
        subagentRunId: UUID,
        eventId: UUID,
        curatedText: String,
        sourceIdsUsedJson: String? = null,
        referencesUsedJson: String? = null,
        toolStatsJson: String? = null,
        occurredAt: Instant = Instant.now()
    ): SubagentRun {
        require(curatedText.isNotBlank()) { "curatedText must be non-empty for succeeded status" }

        val run = requireSubagentRun(subagentRunId)
        if (isDuplicateEvent(eventId, run.briefingRunId, run.id)) {
            return run
        }
        ensureSubagentTransition(run, SubagentRunStatus.SUCCEEDED)

        run.transitionTo(SubagentRunStatus.SUCCEEDED)
        run.curatedText = curatedText
        run.sourceIdsUsedJson = sourceIdsUsedJson
        run.referencesUsedJson = referencesUsedJson
        run.toolStatsJson = toolStatsJson
        run.endedAt = occurredAt
        run.updatedAt = occurredAt
        val saved = subagentRunRepository.save(run)

        recordEvent(
            eventId = eventId,
            briefingRunId = run.briefingRunId,
            subagentRunId = run.id,
            eventType = EVENT_SUBAGENT_COMPLETED,
            occurredAt = occurredAt,
            attempt = saved.attempt,
            payloadJson = payload("status" to saved.status.dbValue)
        )
        return saved
    }

    @Transactional
    fun markSubagentCompletedEmpty(
        subagentRunId: UUID,
        eventId: UUID,
        toolStatsJson: String? = null,
        occurredAt: Instant = Instant.now()
    ): SubagentRun {
        val run = requireSubagentRun(subagentRunId)
        if (isDuplicateEvent(eventId, run.briefingRunId, run.id)) {
            return run
        }
        ensureSubagentTransition(run, SubagentRunStatus.SKIPPED_NO_OUTPUT)

        run.transitionTo(SubagentRunStatus.SKIPPED_NO_OUTPUT)
        run.curatedText = null
        run.toolStatsJson = toolStatsJson
        run.endedAt = occurredAt
        run.updatedAt = occurredAt
        val saved = subagentRunRepository.save(run)

        recordEvent(
            eventId = eventId,
            briefingRunId = run.briefingRunId,
            subagentRunId = run.id,
            eventType = EVENT_SUBAGENT_SKIPPED_NO_OUTPUT,
            occurredAt = occurredAt,
            attempt = saved.attempt,
            payloadJson = payload("status" to saved.status.dbValue)
        )
        return saved
    }

    @Transactional
    fun markSubagentTransientFailedToRetryWait(
        subagentRunId: UUID,
        eventId: UUID,
        errorCode: String,
        errorMessage: String? = null,
        occurredAt: Instant = Instant.now()
    ): SubagentRun {
        val run = requireSubagentRun(subagentRunId)
        if (isDuplicateEvent(eventId, run.briefingRunId, run.id)) {
            return run
        }
        ensureSubagentTransition(run, SubagentRunStatus.RETRY_WAIT)

        run.transitionTo(SubagentRunStatus.RETRY_WAIT)
        run.lastErrorCode = errorCode.take(MAX_ERROR_CODE)
        run.lastErrorRetryable = true
        run.lastErrorMessage = errorMessage?.take(MAX_FAILURE_MESSAGE)
        run.updatedAt = occurredAt
        val saved = subagentRunRepository.save(run)

        recordEvent(
            eventId = eventId,
            briefingRunId = run.briefingRunId,
            subagentRunId = run.id,
            eventType = EVENT_SUBAGENT_RETRY_SCHEDULED,
            occurredAt = occurredAt,
            attempt = saved.attempt,
            payloadJson = payload(
                "status" to saved.status.dbValue,
                "retryable" to true,
                "errorCode" to saved.lastErrorCode
            )
        )
        return saved
    }

    @Transactional
    fun markSubagentRetryDelayElapsed(
        subagentRunId: UUID,
        eventId: UUID,
        occurredAt: Instant = Instant.now()
    ): SubagentRun {
        val run = requireSubagentRun(subagentRunId)
        if (isDuplicateEvent(eventId, run.briefingRunId, run.id)) {
            return run
        }
        ensureSubagentTransition(run, SubagentRunStatus.RUNNING)
        if (run.attempt >= run.maxAttempts) {
            throw ExecutionIllegalTransitionException(
                "Cannot increment attempt for subagentRunId=${run.id}; attempt=${run.attempt} maxAttempts=${run.maxAttempts}"
            )
        }

        run.transitionTo(SubagentRunStatus.RUNNING)
        run.attempt += 1
        run.updatedAt = occurredAt
        val saved = subagentRunRepository.save(run)

        recordEvent(
            eventId = eventId,
            briefingRunId = run.briefingRunId,
            subagentRunId = run.id,
            eventType = EVENT_SUBAGENT_DISPATCHED,
            occurredAt = occurredAt,
            attempt = saved.attempt,
            payloadJson = payload(
                "status" to saved.status.dbValue,
                "resumedFromRetryWait" to true
            )
        )
        return saved
    }

    @Transactional
    fun markSubagentRetryExhaustedSkipped(
        subagentRunId: UUID,
        eventId: UUID,
        errorCode: String,
        errorMessage: String? = null,
        occurredAt: Instant = Instant.now()
    ): SubagentRun {
        val run = requireSubagentRun(subagentRunId)
        if (isDuplicateEvent(eventId, run.briefingRunId, run.id)) {
            return run
        }
        ensureSubagentTransition(run, SubagentRunStatus.SKIPPED)

        run.transitionTo(SubagentRunStatus.SKIPPED)
        run.lastErrorCode = errorCode.take(MAX_ERROR_CODE)
        run.lastErrorRetryable = true
        run.lastErrorMessage = errorMessage?.take(MAX_FAILURE_MESSAGE)
        run.endedAt = occurredAt
        run.updatedAt = occurredAt
        val saved = subagentRunRepository.save(run)

        recordEvent(
            eventId = eventId,
            briefingRunId = run.briefingRunId,
            subagentRunId = run.id,
            eventType = EVENT_SUBAGENT_SKIPPED,
            occurredAt = occurredAt,
            attempt = saved.attempt,
            payloadJson = payload(
                "status" to saved.status.dbValue,
                "retryable" to true,
                "errorCode" to saved.lastErrorCode
            )
        )
        return saved
    }

    @Transactional
    fun markSubagentNonRetryableFailed(
        subagentRunId: UUID,
        eventId: UUID,
        errorCode: String,
        errorMessage: String? = null,
        occurredAt: Instant = Instant.now()
    ): SubagentRun {
        val run = requireSubagentRun(subagentRunId)
        if (isDuplicateEvent(eventId, run.briefingRunId, run.id)) {
            return run
        }
        ensureSubagentTransition(run, SubagentRunStatus.FAILED)

        run.transitionTo(SubagentRunStatus.FAILED)
        run.lastErrorCode = errorCode.take(MAX_ERROR_CODE)
        run.lastErrorRetryable = false
        run.lastErrorMessage = errorMessage?.take(MAX_FAILURE_MESSAGE)
        run.endedAt = occurredAt
        run.updatedAt = occurredAt
        val saved = subagentRunRepository.save(run)

        recordEvent(
            eventId = eventId,
            briefingRunId = run.briefingRunId,
            subagentRunId = run.id,
            eventType = EVENT_SUBAGENT_FAILED,
            occurredAt = occurredAt,
            attempt = saved.attempt,
            payloadJson = payload(
                "status" to saved.status.dbValue,
                "retryable" to false,
                "errorCode" to saved.lastErrorCode
            )
        )
        return saved
    }

    @Transactional
    fun cancelSubagentRun(subagentRunId: UUID, eventId: UUID, occurredAt: Instant = Instant.now()): SubagentRun {
        val run = requireSubagentRun(subagentRunId)
        if (isDuplicateEvent(eventId, run.briefingRunId, run.id)) {
            return run
        }
        ensureSubagentTransition(run, SubagentRunStatus.CANCELLED)

        run.transitionTo(SubagentRunStatus.CANCELLED)
        run.lastErrorCode = ERROR_CODE_CANCELLED
        run.lastErrorRetryable = false
        run.lastErrorMessage = null
        run.endedAt = occurredAt
        run.updatedAt = occurredAt
        val saved = subagentRunRepository.save(run)

        recordEvent(
            eventId = eventId,
            briefingRunId = run.briefingRunId,
            subagentRunId = run.id,
            eventType = EVENT_SUBAGENT_CANCELLED,
            occurredAt = occurredAt,
            attempt = saved.attempt,
            payloadJson = payload("status" to saved.status.dbValue)
        )
        return saved
    }

    @Transactional
    fun markSynthesisGateFailedSkipped(
        synthesisRunId: UUID,
        eventId: UUID,
        requiredForSynthesis: Int,
        actualSucceeded: Int,
        occurredAt: Instant = Instant.now()
    ): SynthesisRun {
        val run = requireSynthesisRun(synthesisRunId)
        if (isDuplicateEvent(eventId, run.briefingRunId, null)) {
            return run
        }
        ensureSynthesisTransition(run, SynthesisRunStatus.SKIPPED)

        run.transitionTo(SynthesisRunStatus.SKIPPED)
        run.endedAt = occurredAt
        run.updatedAt = occurredAt
        val saved = synthesisRunRepository.save(run)

        recordEvent(
            eventId = eventId,
            briefingRunId = run.briefingRunId,
            subagentRunId = null,
            eventType = EVENT_SYNTHESIS_SKIPPED,
            occurredAt = occurredAt,
            payloadJson = payload(
                "status" to saved.status.dbValue,
                "requiredForSynthesis" to requiredForSynthesis,
                "actualSucceeded" to actualSucceeded
            )
        )
        return saved
    }

    @Transactional
    fun startSynthesisRun(synthesisRunId: UUID, eventId: UUID, occurredAt: Instant = Instant.now()): SynthesisRun {
        val run = requireSynthesisRun(synthesisRunId)
        if (isDuplicateEvent(eventId, run.briefingRunId, null)) {
            return run
        }
        ensureSynthesisTransition(run, SynthesisRunStatus.RUNNING)

        run.transitionTo(SynthesisRunStatus.RUNNING)
        run.startedAt = run.startedAt ?: occurredAt
        run.updatedAt = occurredAt
        val saved = synthesisRunRepository.save(run)

        recordEvent(
            eventId = eventId,
            briefingRunId = run.briefingRunId,
            subagentRunId = null,
            eventType = EVENT_SYNTHESIS_STARTED,
            occurredAt = occurredAt,
            payloadJson = payload("status" to saved.status.dbValue)
        )
        return saved
    }

    @Transactional
    fun markSynthesisCompleted(
        synthesisRunId: UUID,
        eventId: UUID,
        output: String,
        occurredAt: Instant = Instant.now()
    ): SynthesisRun {
        val run = requireSynthesisRun(synthesisRunId)
        if (isDuplicateEvent(eventId, run.briefingRunId, null)) {
            return run
        }
        ensureSynthesisTransition(run, SynthesisRunStatus.SUCCEEDED)

        run.transitionTo(SynthesisRunStatus.SUCCEEDED)
        run.output = output
        run.lastErrorCode = null
        run.lastErrorMessage = null
        run.endedAt = occurredAt
        run.updatedAt = occurredAt
        val saved = synthesisRunRepository.save(run)

        recordEvent(
            eventId = eventId,
            briefingRunId = run.briefingRunId,
            subagentRunId = null,
            eventType = EVENT_SYNTHESIS_COMPLETED,
            occurredAt = occurredAt,
            payloadJson = payload("status" to saved.status.dbValue)
        )
        return saved
    }

    @Transactional
    fun markSynthesisFailed(
        synthesisRunId: UUID,
        eventId: UUID,
        errorCode: String? = null,
        errorMessage: String? = null,
        occurredAt: Instant = Instant.now()
    ): SynthesisRun {
        val run = requireSynthesisRun(synthesisRunId)
        if (isDuplicateEvent(eventId, run.briefingRunId, null)) {
            return run
        }
        ensureSynthesisTransition(run, SynthesisRunStatus.FAILED)

        run.transitionTo(SynthesisRunStatus.FAILED)
        run.lastErrorCode = errorCode?.take(MAX_ERROR_CODE)
        run.lastErrorMessage = errorMessage?.take(MAX_FAILURE_MESSAGE)
        run.endedAt = occurredAt
        run.updatedAt = occurredAt
        val saved = synthesisRunRepository.save(run)

        recordEvent(
            eventId = eventId,
            briefingRunId = run.briefingRunId,
            subagentRunId = null,
            eventType = EVENT_SYNTHESIS_FAILED,
            occurredAt = occurredAt,
            payloadJson = payload(
                "status" to saved.status.dbValue,
                "errorCode" to saved.lastErrorCode
            )
        )
        return saved
    }

    @Transactional
    fun cancelSynthesisRun(synthesisRunId: UUID, eventId: UUID, occurredAt: Instant = Instant.now()): SynthesisRun {
        val run = requireSynthesisRun(synthesisRunId)
        if (isDuplicateEvent(eventId, run.briefingRunId, null)) {
            return run
        }
        ensureSynthesisTransition(run, SynthesisRunStatus.CANCELLED)

        run.transitionTo(SynthesisRunStatus.CANCELLED)
        run.endedAt = occurredAt
        run.updatedAt = occurredAt
        val saved = synthesisRunRepository.save(run)

        recordEvent(
            eventId = eventId,
            briefingRunId = run.briefingRunId,
            subagentRunId = null,
            eventType = EVENT_SYNTHESIS_CANCELLED,
            occurredAt = occurredAt,
            payloadJson = payload("status" to saved.status.dbValue)
        )
        return saved
    }

    private fun requireBriefingRun(runId: UUID): BriefingRun {
        return briefingRunRepository.findById(runId).orElseThrow {
            ExecutionRunNotFoundException("BriefingRun", runId)
        }
    }

    private fun requireSubagentRun(runId: UUID): SubagentRun {
        return subagentRunRepository.findById(runId).orElseThrow {
            ExecutionRunNotFoundException("SubagentRun", runId)
        }
    }

    private fun requireSynthesisRun(runId: UUID): SynthesisRun {
        return synthesisRunRepository.findById(runId).orElseThrow {
            ExecutionRunNotFoundException("SynthesisRun", runId)
        }
    }

    private fun ensureBriefingTransition(run: BriefingRun, target: BriefingRunStatus) {
        if (!run.status.canTransitionTo(target)) {
            throw ExecutionIllegalTransitionException(
                "Illegal BriefingRun transition id=${run.id} from=${run.status} to=$target"
            )
        }
    }

    private fun ensureSubagentTransition(run: SubagentRun, target: SubagentRunStatus) {
        if (!run.status.canTransitionTo(target)) {
            throw ExecutionIllegalTransitionException(
                "Illegal SubagentRun transition id=${run.id} from=${run.status} to=$target"
            )
        }
    }

    private fun ensureSynthesisTransition(run: SynthesisRun, target: SynthesisRunStatus) {
        if (!run.status.canTransitionTo(target)) {
            throw ExecutionIllegalTransitionException(
                "Illegal SynthesisRun transition id=${run.id} from=${run.status} to=$target"
            )
        }
    }

    private fun isDuplicateEvent(eventId: UUID, briefingRunId: UUID, subagentRunId: UUID?): Boolean {
        val existing = runEventRepository.findByEventId(eventId) ?: return false
        if (existing.briefingRunId != briefingRunId || existing.subagentRunId != subagentRunId) {
            throw ExecutionIllegalTransitionException(
                "eventId=$eventId already used with different run coordinates"
            )
        }
        return true
    }

    private fun recordEvent(
        eventId: UUID,
        briefingRunId: UUID,
        subagentRunId: UUID?,
        eventType: String,
        occurredAt: Instant,
        attempt: Int? = null,
        payloadJson: String? = null
    ) {
        try {
            runEventRepository.save(
                RunEvent(
                    id = idGenerator.newId(),
                    eventId = eventId,
                    briefingRunId = briefingRunId,
                    subagentRunId = subagentRunId,
                    eventType = eventType,
                    occurredAt = occurredAt,
                    attempt = attempt,
                    payloadJson = payloadJson,
                    createdAt = occurredAt
                )
            )
        } catch (ex: DataIntegrityViolationException) {
            if (runEventRepository.existsByEventId(eventId)) {
                return
            }
            throw ex
        }
    }

    private fun payload(vararg pairs: Pair<String, Any?>): String {
        val mapped = linkedMapOf<String, Any?>()
        pairs.forEach { (key, value) ->
            if (value != null) {
                mapped[key] = value
            }
        }
        return objectMapper.writeValueAsString(mapped)
    }

    companion object {
        private const val MAX_FAILURE_MESSAGE = 2000
        private const val MAX_ERROR_CODE = 64
        private const val ERROR_CODE_CANCELLED = "cancelled"

        private const val EVENT_BRIEFING_RUN_STARTED = "briefing.run.started"
        private const val EVENT_BRIEFING_RUN_CANCEL_REQUESTED = "briefing.run.cancel_requested"
        private const val EVENT_BRIEFING_RUN_TIMED_OUT = "briefing.run.timed_out"
        private const val EVENT_BRIEFING_RUN_COMPLETED = "briefing.run.completed"
        private const val EVENT_BRIEFING_RUN_FAILED = "briefing.run.failed"
        private const val EVENT_BRIEFING_RUN_CANCELLED = "briefing.run.cancelled"

        private const val EVENT_SUBAGENT_DISPATCHED = "subagent.dispatched"
        private const val EVENT_SUBAGENT_COMPLETED = "subagent.completed"
        private const val EVENT_SUBAGENT_RETRY_SCHEDULED = "subagent.retry.scheduled"
        private const val EVENT_SUBAGENT_SKIPPED = "subagent.skipped"
        private const val EVENT_SUBAGENT_SKIPPED_NO_OUTPUT = "subagent.skipped_no_output"
        private const val EVENT_SUBAGENT_FAILED = "subagent.failed"
        private const val EVENT_SUBAGENT_CANCELLED = "subagent.cancelled"

        private const val EVENT_SYNTHESIS_STARTED = "synthesis.started"
        private const val EVENT_SYNTHESIS_COMPLETED = "synthesis.completed"
        private const val EVENT_SYNTHESIS_FAILED = "synthesis.failed"
        private const val EVENT_SYNTHESIS_SKIPPED = "synthesis.skipped"
        private const val EVENT_SYNTHESIS_CANCELLED = "synthesis.cancelled"
    }
}
