package com.briefy.api.application.briefing

import com.briefy.api.domain.knowledgegraph.briefing.*
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.infrastructure.id.IdGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class BriefingExecutionOrchestratorService(
    private val briefingRunRepository: BriefingRunRepository,
    private val subagentRunRepository: SubagentRunRepository,
    private val synthesisRunRepository: SynthesisRunRepository,
    private val briefingPlanStepRepository: BriefingPlanStepRepository,
    private val executionStateTransitionService: ExecutionStateTransitionService,
    private val subagentExecutionRunner: SubagentExecutionRunner,
    private val synthesisExecutionRunner: SynthesisExecutionRunner,
    private val executionFingerprintService: ExecutionFingerprintService,
    private val idGenerator: IdGenerator,
    private val objectMapper: ObjectMapper
) {

    fun executeApprovedBriefing(
        briefing: Briefing,
        orderedSources: List<Source>,
        planSteps: List<BriefingPlanStep>
    ): ExecutionOrchestrationOutcome {
        if (planSteps.isEmpty()) {
            return ExecutionOrchestrationOutcome.failed(
                failureCode = BriefingRunFailureCode.ORCHESTRATION_ERROR,
                failureMessage = "Briefing plan must include at least one step"
            )
        }

        val bootstrapped = bootstrapOrLoadRunGraph(
            briefing = briefing,
            orderedSources = orderedSources,
            planSteps = planSteps
        )
        if (isRunPastDeadline(bootstrapped.briefingRun)) {
            handleGlobalTimeout(bootstrapped)
            return ExecutionOrchestrationOutcome.failed(
                failureCode = BriefingRunFailureCode.GLOBAL_TIMEOUT,
                failureMessage = "Execution run exceeded global timeout"
            )
        }
        if (bootstrapped.briefingRun.status == BriefingRunStatus.CANCELLING) {
            finalizeCancellingRun(bootstrapped)
            return ExecutionOrchestrationOutcome.failed(
                failureCode = BriefingRunFailureCode.CANCELLED,
                failureMessage = "Execution run is cancelling; refusing to dispatch additional work"
            )
        }

        val stepByPersonaKey = planSteps.associateBy { personaKeyForStep(it) }

        if (bootstrapped.briefingRun.status == BriefingRunStatus.QUEUED) {
            executionStateTransitionService.startBriefingRun(
                runId = bootstrapped.briefingRun.id,
                eventId = nextEventId(),
                occurredAt = Instant.now()
            )
        }

        val mutablePlanSteps = planSteps.toMutableList()
        bootstrapped.subagentRuns.forEach { subagentRun ->
            val step = stepByPersonaKey[subagentRun.personaKey]
                ?: return@forEach

            when (subagentRun.status) {
                SubagentRunStatus.PENDING, SubagentRunStatus.RUNNING, SubagentRunStatus.RETRY_WAIT -> {
                    executeSubagent(
                        briefingRunId = bootstrapped.briefingRun.id,
                        subagentRun = subagentRun,
                        step = step,
                        briefing = briefing,
                        orderedSources = orderedSources
                    )
                }

                SubagentRunStatus.SUCCEEDED -> ensureStepTerminal(step, true)
                SubagentRunStatus.FAILED,
                SubagentRunStatus.SKIPPED,
                SubagentRunStatus.SKIPPED_NO_OUTPUT,
                SubagentRunStatus.CANCELLED -> ensureStepTerminal(step, false)
            }
        }

        briefingPlanStepStatusesPersist(mutablePlanSteps)

        val refreshedBriefingRun = briefingRunRepository.findById(bootstrapped.briefingRun.id)
            .orElseThrow { ExecutionRunNotFoundException("BriefingRun", bootstrapped.briefingRun.id) }
        val refreshedSubagentRuns = subagentRunRepository.findByBriefingRunIdOrderByCreatedAtAsc(bootstrapped.briefingRun.id)
        val nonEmptySucceededCount = refreshedSubagentRuns.count { it.status == SubagentRunStatus.SUCCEEDED }

        refreshedBriefingRun.nonEmptySucceededCount = nonEmptySucceededCount
        refreshedBriefingRun.updatedAt = Instant.now()
        briefingRunRepository.save(refreshedBriefingRun)

        val allTerminal = refreshedSubagentRuns.all { it.status.isTerminal() }
        if (!allTerminal) {
            return ExecutionOrchestrationOutcome.failed(
                failureCode = BriefingRunFailureCode.ORCHESTRATION_ERROR,
                failureMessage = "Subagent fan-out finished with non-terminal runs"
            )
        }

        val synthesisRun = synthesisRunRepository.findByBriefingRunId(refreshedBriefingRun.id)
            ?: return ExecutionOrchestrationOutcome.failed(
                failureCode = BriefingRunFailureCode.ORCHESTRATION_ERROR,
                failureMessage = "Synthesis run missing for briefingRunId=${refreshedBriefingRun.id}"
            )
        if (isRunPastDeadline(refreshedBriefingRun)) {
            handleGlobalTimeout(
                RunGraph(
                    briefingRun = refreshedBriefingRun,
                    subagentRuns = refreshedSubagentRuns,
                    synthesisRun = synthesisRun
                )
            )
            return ExecutionOrchestrationOutcome.failed(
                failureCode = BriefingRunFailureCode.GLOBAL_TIMEOUT,
                failureMessage = "Execution run exceeded global timeout before synthesis"
            )
        }
        if (refreshedBriefingRun.status == BriefingRunStatus.CANCELLING) {
            finalizeCancellingRun(
                RunGraph(
                    briefingRun = refreshedBriefingRun,
                    subagentRuns = refreshedSubagentRuns,
                    synthesisRun = synthesisRun
                )
            )
            return ExecutionOrchestrationOutcome.failed(
                failureCode = BriefingRunFailureCode.CANCELLED,
                failureMessage = "Execution run cancelled before synthesis"
            )
        }

        val required = refreshedBriefingRun.requiredForSynthesis
        val actual = nonEmptySucceededCount
        if (actual < required) {
            val now = Instant.now()
            executionStateTransitionService.markSynthesisGateFailedSkipped(
                synthesisRunId = synthesisRun.id,
                eventId = nextEventId(),
                requiredForSynthesis = required,
                actualSucceeded = actual,
                occurredAt = now
            )
            executionStateTransitionService.markBriefingRunFailed(
                runId = refreshedBriefingRun.id,
                eventId = nextEventId(),
                failureCode = BriefingRunFailureCode.SYNTHESIS_GATE_NOT_MET,
                failureMessage = "synthesis gate not met required=$required actual=$actual",
                occurredAt = now
            )
            return ExecutionOrchestrationOutcome.failed(
                failureCode = BriefingRunFailureCode.SYNTHESIS_GATE_NOT_MET,
                failureMessage = "Synthesis gate not met"
            )
        }

        val succeededPersonaKeys = refreshedSubagentRuns
            .filter { it.status == SubagentRunStatus.SUCCEEDED }
            .map { it.personaKey }
        val excludedPersonaKeys = refreshedSubagentRuns
            .filter { it.status != SubagentRunStatus.SUCCEEDED }
            .map { it.personaKey }

        synthesisRun.inputPersonaCount = succeededPersonaKeys.size
        synthesisRun.includedPersonaKeysJson = objectMapper.writeValueAsString(succeededPersonaKeys)
        synthesisRun.excludedPersonaKeysJson = objectMapper.writeValueAsString(excludedPersonaKeys)
        synthesisRun.updatedAt = Instant.now()
        synthesisRunRepository.save(synthesisRun)

        val synthesisRequest = BriefingGenerationRequest(
            briefingId = briefing.id,
            userId = briefing.userId,
            enrichmentIntent = briefing.enrichmentIntent.name,
            sources = orderedSources.map { source ->
                BriefingSourceInput(
                    sourceId = source.id,
                    title = source.metadata?.title ?: source.url.normalized,
                    url = source.url.normalized,
                    text = source.content?.text.orEmpty()
                )
            },
            plan = planSteps.sortedBy { it.stepOrder }.map { step ->
                BriefingPlanInput(
                    personaName = step.personaName,
                    task = step.task,
                    stepOrder = step.stepOrder
                )
            }
        )

        val synthesisStartAt = Instant.now()
        executionStateTransitionService.startSynthesisRun(
            synthesisRunId = synthesisRun.id,
            eventId = nextEventId(),
            occurredAt = synthesisStartAt
        )

        return runCatching {
            val generationResult = synthesisExecutionRunner.run(synthesisRequest)
            val completedAt = Instant.now()
            executionStateTransitionService.markSynthesisCompleted(
                synthesisRunId = synthesisRun.id,
                eventId = nextEventId(),
                output = generationResult.markdownBody,
                occurredAt = completedAt
            )
            executionStateTransitionService.markBriefingRunSucceeded(
                runId = refreshedBriefingRun.id,
                eventId = nextEventId(),
                occurredAt = completedAt
            )

            ExecutionOrchestrationOutcome.succeeded(
                briefingRunId = refreshedBriefingRun.id,
                generationResult = generationResult
            )
        }.getOrElse { error ->
            val failedAt = Instant.now()
            executionStateTransitionService.markSynthesisFailed(
                synthesisRunId = synthesisRun.id,
                eventId = nextEventId(),
                errorCode = "synthesis_runner_error",
                errorMessage = error.message,
                occurredAt = failedAt
            )
            executionStateTransitionService.markBriefingRunFailed(
                runId = refreshedBriefingRun.id,
                eventId = nextEventId(),
                failureCode = BriefingRunFailureCode.SYNTHESIS_FAILED,
                failureMessage = error.message,
                occurredAt = failedAt
            )
            ExecutionOrchestrationOutcome.failed(
                failureCode = BriefingRunFailureCode.SYNTHESIS_FAILED,
                failureMessage = error.message ?: "Synthesis failed"
            )
        }
    }

    private fun executeSubagent(
        briefingRunId: UUID,
        subagentRun: SubagentRun,
        step: BriefingPlanStep,
        briefing: Briefing,
        orderedSources: List<Source>
    ) {
        val now = Instant.now()
        if (step.status == BriefingPlanStepStatus.PLANNED) {
            step.markRunning(now)
        }
        while (true) {
            val run = briefingRunRepository.findById(briefingRunId)
                .orElseThrow { ExecutionRunNotFoundException("BriefingRun", briefingRunId) }
            if (isRunPastDeadline(run)) {
                executionStateTransitionService.markBriefingRunTimedOut(
                    runId = run.id,
                    eventId = nextEventId(),
                    failureMessage = "Global timeout reached while executing subagent ${subagentRun.personaKey}",
                    occurredAt = Instant.now()
                )
                return
            }
            if (run.status == BriefingRunStatus.CANCELLING) {
                if (subagentRunRepository.findById(subagentRun.id).orElseThrow().status.let { !it.isTerminal() }) {
                    executionStateTransitionService.cancelSubagentRun(
                        subagentRunId = subagentRun.id,
                        eventId = nextEventId(),
                        occurredAt = Instant.now()
                    )
                }
                step.markFailed(Instant.now())
                return
            }

            val currentSubagentRun = subagentRunRepository.findById(subagentRun.id)
                .orElseThrow { ExecutionRunNotFoundException("SubagentRun", subagentRun.id) }
            if (currentSubagentRun.status.isTerminal()) {
                if (currentSubagentRun.status == SubagentRunStatus.SUCCEEDED) {
                    step.markSucceeded(Instant.now())
                } else {
                    step.markFailed(Instant.now())
                }
                return
            }

            val attemptStart = Instant.now()
            if (currentSubagentRun.status == SubagentRunStatus.PENDING || currentSubagentRun.status == SubagentRunStatus.RETRY_WAIT) {
                if (currentSubagentRun.status == SubagentRunStatus.RETRY_WAIT) {
                    executionStateTransitionService.markSubagentRetryDelayElapsed(
                        subagentRunId = currentSubagentRun.id,
                        eventId = nextEventId(),
                        occurredAt = attemptStart
                    )
                } else {
                    executionStateTransitionService.dispatchSubagentRun(
                        subagentRunId = currentSubagentRun.id,
                        eventId = nextEventId(),
                        occurredAt = attemptStart
                    )
                }
            }

            val runningSubagentRun = subagentRunRepository.findById(subagentRun.id)
                .orElseThrow { ExecutionRunNotFoundException("SubagentRun", subagentRun.id) }
            runningSubagentRun.deadlineAt = attemptStart.plusSeconds(SUBAGENT_TIMEOUT_SECONDS)
            runningSubagentRun.updatedAt = attemptStart
            subagentRunRepository.save(runningSubagentRun)

            val result = subagentExecutionRunner.execute(
                SubagentExecutionContext(
                    briefingId = briefing.id,
                    briefingRunId = runningSubagentRun.briefingRunId,
                    subagentRunId = runningSubagentRun.id,
                    personaKey = runningSubagentRun.personaKey,
                    personaName = step.personaName,
                    task = step.task,
                    sources = orderedSources.map { source ->
                        BriefingSourceInput(
                            sourceId = source.id,
                            title = source.metadata?.title ?: source.url.normalized,
                            url = source.url.normalized,
                            text = source.content?.text.orEmpty()
                        )
                    }
                )
            )

            val occurredAt = Instant.now()
            when (result) {
                is SubagentExecutionResult.Succeeded -> {
                    executionStateTransitionService.markSubagentCompletedNonEmpty(
                        subagentRunId = runningSubagentRun.id,
                        eventId = nextEventId(),
                        curatedText = result.curatedText,
                        sourceIdsUsedJson = result.sourceIdsUsedJson,
                        referencesUsedJson = result.referencesUsedJson,
                        toolStatsJson = result.toolStatsJson,
                        occurredAt = occurredAt
                    )
                    step.markSucceeded(occurredAt)
                    return
                }

                SubagentExecutionResult.EmptyOutput -> {
                    executionStateTransitionService.markSubagentCompletedEmpty(
                        subagentRunId = runningSubagentRun.id,
                        eventId = nextEventId(),
                        occurredAt = occurredAt
                    )
                    step.markFailed(occurredAt)
                    return
                }

                is SubagentExecutionResult.Failed -> {
                    if (!isRetryable(result.errorCode)) {
                        executionStateTransitionService.markSubagentNonRetryableFailed(
                            subagentRunId = runningSubagentRun.id,
                            eventId = nextEventId(),
                            errorCode = result.errorCode,
                            errorMessage = result.errorMessage,
                            occurredAt = occurredAt
                        )
                        step.markFailed(occurredAt)
                        return
                    }

                    val exhausted = runningSubagentRun.attempt >= runningSubagentRun.maxAttempts
                    val retryDelaySeconds = computeRetryDelaySeconds(
                        errorCode = result.errorCode,
                        attempt = runningSubagentRun.attempt,
                        errorMessage = result.errorMessage
                    )
                    val attemptDeadline = runningSubagentRun.deadlineAt ?: occurredAt.plusSeconds(SUBAGENT_TIMEOUT_SECONDS)
                    val retryAt = occurredAt.plusSeconds(retryDelaySeconds)
                    if (exhausted || retryAt.isAfter(attemptDeadline)) {
                        executionStateTransitionService.markSubagentRetryExhaustedSkipped(
                            subagentRunId = runningSubagentRun.id,
                            eventId = nextEventId(),
                            errorCode = result.errorCode,
                            errorMessage = result.errorMessage,
                            occurredAt = occurredAt
                        )
                        step.markFailed(occurredAt)
                        return
                    }

                    executionStateTransitionService.markSubagentTransientFailedToRetryWait(
                        subagentRunId = runningSubagentRun.id,
                        eventId = nextEventId(),
                        errorCode = result.errorCode,
                        errorMessage = result.errorMessage,
                        occurredAt = occurredAt
                    )
                    executionStateTransitionService.markSubagentRetryDelayElapsed(
                        subagentRunId = runningSubagentRun.id,
                        eventId = nextEventId(),
                        occurredAt = retryAt
                    )
                }
            }
        }
    }

    private fun finalizeCancellingRun(runGraph: RunGraph) {
        val occurredAt = Instant.now()

        runGraph.subagentRuns
            .filter { it.status == SubagentRunStatus.PENDING || it.status == SubagentRunStatus.RUNNING || it.status == SubagentRunStatus.RETRY_WAIT }
            .forEach { subagentRun ->
                executionStateTransitionService.cancelSubagentRun(
                    subagentRunId = subagentRun.id,
                    eventId = nextEventId(),
                    occurredAt = occurredAt
                )
            }

        if (runGraph.synthesisRun.status == SynthesisRunStatus.NOT_STARTED || runGraph.synthesisRun.status == SynthesisRunStatus.RUNNING) {
            executionStateTransitionService.cancelSynthesisRun(
                synthesisRunId = runGraph.synthesisRun.id,
                eventId = nextEventId(),
                occurredAt = occurredAt
            )
        }

        executionStateTransitionService.markBriefingRunCancelled(
            runId = runGraph.briefingRun.id,
            eventId = nextEventId(),
            occurredAt = occurredAt
        )
    }

    private fun bootstrapOrLoadRunGraph(
        briefing: Briefing,
        orderedSources: List<Source>,
        planSteps: List<BriefingPlanStep>
    ): RunGraph {
        val activeRun = findActiveRun(briefing.id)
        if (activeRun != null) {
            return loadExistingRunGraph(activeRun)
        }

        val now = Instant.now()
        val fingerprint = executionFingerprintService.compute(briefing, orderedSources, planSteps)
        val totalPersonas = planSteps.size
        val requiredForSynthesis = computeRequiredForSynthesis(totalPersonas)
        val runDeadline = now.plusSeconds(GLOBAL_TIMEOUT_SECONDS)

        val run = try {
            briefingRunRepository.save(
                BriefingRun(
                    id = idGenerator.newId(),
                    briefingId = briefing.id,
                    executionFingerprint = fingerprint,
                    status = BriefingRunStatus.QUEUED,
                    createdAt = now,
                    updatedAt = now,
                    deadlineAt = runDeadline,
                    totalPersonas = totalPersonas,
                    requiredForSynthesis = requiredForSynthesis,
                    nonEmptySucceededCount = 0
                )
            )
        } catch (ex: DataIntegrityViolationException) {
            val existing = findActiveRun(briefing.id) ?: throw ex
            return loadExistingRunGraph(existing)
        }

        val subagentRuns = subagentRunRepository.saveAll(
            planSteps.sortedBy { it.stepOrder }.map { step ->
                SubagentRun(
                    id = idGenerator.newId(),
                    briefingRunId = run.id,
                    briefingId = briefing.id,
                    personaKey = personaKeyForStep(step),
                    status = SubagentRunStatus.PENDING,
                    attempt = 1,
                    maxAttempts = 3,
                    deadlineAt = now.plusSeconds(SUBAGENT_TIMEOUT_SECONDS),
                    createdAt = now,
                    updatedAt = now
                )
            }
        )

        val synthesisRun = synthesisRunRepository.save(
            SynthesisRun(
                id = idGenerator.newId(),
                briefingRunId = run.id,
                status = SynthesisRunStatus.NOT_STARTED,
                inputPersonaCount = 0,
                createdAt = now,
                updatedAt = now
            )
        )

        return RunGraph(
            briefingRun = run,
            subagentRuns = subagentRuns.sortedBy { it.createdAt },
            synthesisRun = synthesisRun
        )
    }

    private fun loadExistingRunGraph(activeRun: BriefingRun): RunGraph {
        val subagentRuns = subagentRunRepository.findByBriefingRunIdOrderByCreatedAtAsc(activeRun.id)
        val synthesisRun = synthesisRunRepository.findByBriefingRunId(activeRun.id)
            ?: throw ExecutionIllegalTransitionException("Synthesis run missing for active run ${activeRun.id}")
        return RunGraph(
            briefingRun = activeRun,
            subagentRuns = subagentRuns,
            synthesisRun = synthesisRun
        )
    }

    private fun findActiveRun(briefingId: UUID): BriefingRun? {
        return briefingRunRepository.findTopByBriefingIdAndStatusInOrderByCreatedAtDesc(
            briefingId = briefingId,
            statuses = ACTIVE_RUN_STATUSES
        )
    }

    private fun briefingPlanStepStatusesPersist(planSteps: List<BriefingPlanStep>) {
        if (planSteps.isEmpty()) {
            return
        }
        briefingPlanStepRepository.saveAll(planSteps)
    }

    private fun ensureStepTerminal(step: BriefingPlanStep, succeeded: Boolean) {
        if (step.status == BriefingPlanStepStatus.PLANNED) {
            step.markRunning(Instant.now())
        }
        if (step.status != BriefingPlanStepStatus.RUNNING) {
            return
        }

        if (succeeded) {
            step.markSucceeded(Instant.now())
        } else {
            step.markFailed(Instant.now())
        }
    }

    private fun computeRequiredForSynthesis(totalPersonas: Int): Int {
        return (totalPersonas + 1) / 2
    }

    private fun personaKeyForStep(step: BriefingPlanStep): String {
        return "step-${step.stepOrder}"
    }

    private fun nextEventId(): UUID = idGenerator.newId()

    private fun isRunPastDeadline(run: BriefingRun, now: Instant = Instant.now()): Boolean {
        val deadline = run.deadlineAt ?: run.createdAt.plusSeconds(GLOBAL_TIMEOUT_SECONDS)
        return now.isAfter(deadline)
    }

    private fun handleGlobalTimeout(runGraph: RunGraph) {
        val now = Instant.now()
        executionStateTransitionService.markBriefingRunTimedOut(
            runId = runGraph.briefingRun.id,
            eventId = nextEventId(),
            failureMessage = "Execution run exceeded global timeout",
            occurredAt = now
        )

        runGraph.subagentRuns
            .filter { !it.status.isTerminal() }
            .forEach { subagentRun ->
                executionStateTransitionService.cancelSubagentRun(
                    subagentRunId = subagentRun.id,
                    eventId = nextEventId(),
                    occurredAt = now
                )
            }

        if (runGraph.synthesisRun.status == SynthesisRunStatus.NOT_STARTED || runGraph.synthesisRun.status == SynthesisRunStatus.RUNNING) {
            executionStateTransitionService.cancelSynthesisRun(
                synthesisRunId = runGraph.synthesisRun.id,
                eventId = nextEventId(),
                occurredAt = now
            )
        }
    }

    private fun isRetryable(errorCode: String): Boolean {
        // TODO(briefing-execution): Move retry classification to runner contract when we implement full AI execution.
        return errorCode in RETRYABLE_ERROR_CODES
    }

    private fun computeRetryDelaySeconds(errorCode: String, attempt: Int, errorMessage: String?): Long {
        if (errorCode == ERROR_HTTP_429) {
            val retryAfter = parseRetryAfterSeconds(errorMessage)
            if (retryAfter != null) {
                return retryAfter.coerceAtLeast(1L)
            }
            return when (attempt) {
                1 -> HTTP_429_FALLBACK_DELAY_FIRST_SECONDS
                else -> HTTP_429_FALLBACK_DELAY_SECOND_SECONDS
            }
        }
        return when (attempt) {
            1 -> TRANSIENT_RETRY_DELAY_FIRST_SECONDS
            else -> TRANSIENT_RETRY_DELAY_SECOND_SECONDS
        }
    }

    private fun parseRetryAfterSeconds(errorMessage: String?): Long? {
        // TODO(briefing-execution): Replace this lightweight parser with structured provider metadata once available.
        if (errorMessage.isNullOrBlank()) {
            return null
        }
        val marker = "retry_after="
        val start = errorMessage.lowercase().indexOf(marker)
        if (start == -1) {
            return null
        }
        val valueStart = start + marker.length
        val digits = buildString {
            for (i in valueStart until errorMessage.length) {
                val ch = errorMessage[i]
                if (!ch.isDigit()) {
                    break
                }
                append(ch)
            }
        }
        if (digits.isBlank()) {
            return null
        }
        return runCatching { digits.toLong() }.getOrNull()
    }

    companion object {
        // TODO(briefing-execution): Externalize these constants to configuration properties after Slice 4 hardening.
        private const val GLOBAL_TIMEOUT_SECONDS = 180L
        private const val SUBAGENT_TIMEOUT_SECONDS = 90L
        private const val TRANSIENT_RETRY_DELAY_FIRST_SECONDS = 1L
        private const val TRANSIENT_RETRY_DELAY_SECOND_SECONDS = 2L
        private const val HTTP_429_FALLBACK_DELAY_FIRST_SECONDS = 2L
        private const val HTTP_429_FALLBACK_DELAY_SECOND_SECONDS = 4L
        private const val ERROR_TIMEOUT = "timeout"
        private const val ERROR_HTTP_429 = "http_429"
        private const val ERROR_HTTP_5XX = "http_5xx"
        private const val ERROR_NETWORK = "network_error"
        private val RETRYABLE_ERROR_CODES = setOf(
            ERROR_TIMEOUT,
            ERROR_HTTP_429,
            ERROR_HTTP_5XX,
            ERROR_NETWORK
        )
        private val ACTIVE_RUN_STATUSES = listOf(
            BriefingRunStatus.QUEUED,
            BriefingRunStatus.RUNNING,
            BriefingRunStatus.CANCELLING
        )
    }

    private data class RunGraph(
        val briefingRun: BriefingRun,
        val subagentRuns: List<SubagentRun>,
        val synthesisRun: SynthesisRun
    )
}

data class ExecutionOrchestrationOutcome(
    val status: Status,
    val briefingRunId: UUID?,
    val generationResult: BriefingGenerationResult?,
    val failureCode: BriefingRunFailureCode?,
    val failureMessage: String?
) {
    enum class Status {
        SUCCEEDED,
        FAILED
    }

    companion object {
        fun succeeded(
            briefingRunId: UUID,
            generationResult: BriefingGenerationResult
        ): ExecutionOrchestrationOutcome {
            return ExecutionOrchestrationOutcome(
                status = Status.SUCCEEDED,
                briefingRunId = briefingRunId,
                generationResult = generationResult,
                failureCode = null,
                failureMessage = null
            )
        }

        fun failed(
            failureCode: BriefingRunFailureCode,
            failureMessage: String
        ): ExecutionOrchestrationOutcome {
            return ExecutionOrchestrationOutcome(
                status = Status.FAILED,
                briefingRunId = null,
                generationResult = null,
                failureCode = failureCode,
                failureMessage = failureMessage
            )
        }
    }
}
