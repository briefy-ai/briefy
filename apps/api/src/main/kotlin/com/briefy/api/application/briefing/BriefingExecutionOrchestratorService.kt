package com.briefy.api.application.briefing

import com.briefy.api.domain.knowledgegraph.briefing.*
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.infrastructure.ai.setAttributeIfNotBlank
import com.briefy.api.infrastructure.ai.setAttributeIfNotNull
import com.briefy.api.infrastructure.ai.withSpan
import com.briefy.api.infrastructure.id.IdGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
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
    private val executionConfig: ExecutionConfigProperties,
    private val sourceRepository: SourceRepository,
    private val idGenerator: IdGenerator,
    private val objectMapper: ObjectMapper,
    private val tracer: Tracer
) {

    fun executeApprovedBriefing(
        briefing: Briefing,
        orderedSources: List<Source>,
        planSteps: List<BriefingPlanStep>
    ): ExecutionOrchestrationOutcome {
        return tracer.withSpan(
            name = "briefing.generation",
            noParent = true,
            configure = { span ->
                span.setAttribute("briefing.id", briefing.id.toString())
                span.setAttribute("langfuse.user.id", briefing.userId.toString())
                span.setAttribute("briefing.execution.total_personas", planSteps.size.toLong())
                span.setAttribute("briefing.execution.required_for_synthesis", computeRequiredForSynthesis(planSteps.size).toLong())
                span.setAttribute("briefing.source_count", orderedSources.size.toLong())
            }
        ) { span ->
            val outcome = executeApprovedBriefingInTrace(briefing, orderedSources, planSteps)
            outcome.briefingRunId?.let { span.setAttribute("briefing.run.id", it.toString()) }
            span.setAttribute("briefing.execution.outcome", outcome.status.name.lowercase())
            span.setAttributeIfNotBlank("briefing.execution.failure_code", outcome.failureCode?.dbValue)
            span.setAttributeIfNotBlank("briefing.execution.failure_message", outcome.failureMessage)
            if (outcome.status == ExecutionOrchestrationOutcome.Status.SUCCEEDED) {
                span.setStatus(StatusCode.OK)
            } else {
                span.setStatus(StatusCode.ERROR, outcome.failureCode?.dbValue ?: "failed")
            }
            outcome
        }
    }

    private fun executeApprovedBriefingInTrace(
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
                briefingRunId = bootstrapped.briefingRun.id,
                failureCode = BriefingRunFailureCode.GLOBAL_TIMEOUT,
                failureMessage = "Execution run exceeded global timeout"
            )
        }
        if (bootstrapped.briefingRun.status == BriefingRunStatus.CANCELLING) {
            finalizeCancellingRun(bootstrapped)
            return ExecutionOrchestrationOutcome.failed(
                briefingRunId = bootstrapped.briefingRun.id,
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
        try {
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
        } catch (ex: ExecutionDeadlineExceededException) {
            return ExecutionOrchestrationOutcome.failed(
                briefingRunId = bootstrapped.briefingRun.id,
                failureCode = BriefingRunFailureCode.GLOBAL_TIMEOUT,
                failureMessage = "Execution run exceeded global timeout"
            )
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
                briefingRunId = refreshedBriefingRun.id,
                failureCode = BriefingRunFailureCode.ORCHESTRATION_ERROR,
                failureMessage = "Subagent fan-out finished with non-terminal runs"
            )
        }

        val synthesisRun = synthesisRunRepository.findByBriefingRunId(refreshedBriefingRun.id)
            ?: return ExecutionOrchestrationOutcome.failed(
                briefingRunId = refreshedBriefingRun.id,
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
                briefingRunId = refreshedBriefingRun.id,
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
                briefingRunId = refreshedBriefingRun.id,
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
                briefingRunId = refreshedBriefingRun.id,
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

        val synthesisSources = buildSynthesisSources(briefing.userId, orderedSources, refreshedSubagentRuns)
        val successfulOutputs = buildSuccessfulSubagentOutputs(refreshedSubagentRuns, stepByPersonaKey)
        val synthesisRequest = BriefingGenerationRequest(
            briefingId = briefing.id,
            userId = briefing.userId,
            enrichmentIntent = briefing.enrichmentIntent.name,
            sources = synthesisSources.map { source ->
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
            },
            subagentOutputs = successfulOutputs
        )

        val synthesisStartAt = Instant.now()
        executionStateTransitionService.startSynthesisRun(
            synthesisRunId = synthesisRun.id,
            eventId = nextEventId(),
            occurredAt = synthesisStartAt
        )

        return runCatching {
            tracer.withSpan(
                name = "synthesis",
                configure = { span ->
                    span.setAttribute("briefing.run.id", refreshedBriefingRun.id.toString())
                    span.setAttribute("input.persona.count", succeededPersonaKeys.size.toLong())
                    span.setAttribute("synthesis.excluded_persona_count", excludedPersonaKeys.size.toLong())
                }
            ) { span ->
                val generationResult = synthesisExecutionRunner.run(synthesisRequest)
                span.setStatus(StatusCode.OK)
                generationResult
            }
        }.map { generationResult ->
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
                generationResult = generationResult,
                citationSources = synthesisSources
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
                briefingRunId = refreshedBriefingRun.id,
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
            handleGlobalTimeout(buildRunGraph(run))
            throw ExecutionDeadlineExceededException()
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
            runningSubagentRun.deadlineAt = attemptStart.plusSeconds(executionConfig.subagentTimeoutSeconds)
            runningSubagentRun.updatedAt = attemptStart
            subagentRunRepository.save(runningSubagentRun)

            val executionContext = SubagentExecutionContext(
                briefingId = briefing.id,
                briefingRunId = runningSubagentRun.briefingRunId,
                subagentRunId = runningSubagentRun.id,
                userId = briefing.userId,
                attempt = runningSubagentRun.attempt,
                maxAttempts = runningSubagentRun.maxAttempts,
                retryReason = runningSubagentRun.lastErrorCode,
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

            val result = tracer.withSpan(
                name = "subagent.${runningSubagentRun.personaKey}",
                configure = { span ->
                    span.setAttribute("briefing.run.id", runningSubagentRun.briefingRunId.toString())
                    span.setAttribute("subagent.run.id", runningSubagentRun.id.toString())
                    span.setAttribute("persona.key", runningSubagentRun.personaKey)
                    span.setAttribute("attempt.number", runningSubagentRun.attempt.toLong())
                    span.setAttribute("attempt.max", runningSubagentRun.maxAttempts.toLong())
                    span.setAttribute("retry", runningSubagentRun.attempt > 1)
                    span.setAttributeIfNotBlank("retry.reason", runningSubagentRun.lastErrorCode)
                }
            ) { span ->
                subagentExecutionRunner.execute(executionContext).also { executionResult ->
                    when (executionResult) {
                        is SubagentExecutionResult.Succeeded -> {
                            span.setAttribute("subagent.result", "succeeded")
                            span.setStatus(StatusCode.OK)
                        }

                        SubagentExecutionResult.EmptyOutput -> {
                            span.setAttribute("subagent.result", "empty_output")
                            span.setStatus(StatusCode.ERROR, "empty_output")
                        }

                        is SubagentExecutionResult.Failed -> {
                            span.setAttribute("subagent.result", "failed")
                            span.setAttribute("subagent.retryable", executionResult.retryable)
                            span.setAttribute("subagent.error.code", executionResult.errorCode)
                            span.setAttributeIfNotBlank("subagent.error.message", executionResult.errorMessage)
                            span.setAttributeIfNotNull("subagent.retry_after_seconds", executionResult.retryAfterSeconds)
                            span.setStatus(StatusCode.ERROR, executionResult.errorCode)
                        }
                    }
                }
            }

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
                    executionStateTransitionService.recordSubagentAttemptFailed(
                        subagentRunId = runningSubagentRun.id,
                        eventId = nextEventId(),
                        errorCode = result.errorCode,
                        errorMessage = result.errorMessage,
                        retryable = result.retryable,
                        retryAfterSeconds = result.retryAfterSeconds,
                        attempt = runningSubagentRun.attempt,
                        occurredAt = occurredAt
                    )

                    if (!result.retryable) {
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
                    val retryDelaySeconds = computeRetryDelaySeconds(result, runningSubagentRun.attempt)
                    val attemptDeadline = runningSubagentRun.deadlineAt ?: occurredAt.plusSeconds(executionConfig.subagentTimeoutSeconds)
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
        val runDeadline = now.plusSeconds(executionConfig.globalTimeoutSeconds)

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
                    maxAttempts = executionConfig.maxAttempts,
                    deadlineAt = now.plusSeconds(executionConfig.subagentTimeoutSeconds),
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

    private fun buildRunGraph(run: BriefingRun): RunGraph {
        val subagentRuns = subagentRunRepository.findByBriefingRunIdOrderByCreatedAtAsc(run.id)
        val synthesisRun = synthesisRunRepository.findByBriefingRunId(run.id)
            ?: throw ExecutionIllegalTransitionException("Synthesis run missing for run ${run.id}")
        return RunGraph(
            briefingRun = run,
            subagentRuns = subagentRuns,
            synthesisRun = synthesisRun
        )
    }

    private class ExecutionDeadlineExceededException : RuntimeException()

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

    private fun buildSuccessfulSubagentOutputs(
        subagentRuns: List<SubagentRun>,
        stepByPersonaKey: Map<String, BriefingPlanStep>
    ): List<BriefingSubagentOutputInput> {
        return subagentRuns
            .filter { it.status == SubagentRunStatus.SUCCEEDED }
            .mapNotNull { subagentRun ->
                val step = stepByPersonaKey[subagentRun.personaKey] ?: return@mapNotNull null
                val curatedText = subagentRun.curatedText?.trim().orEmpty()
                if (curatedText.isBlank()) {
                    return@mapNotNull null
                }
                BriefingSubagentOutputInput(
                    personaKey = subagentRun.personaKey,
                    personaName = step.personaName,
                    task = step.task,
                    curatedText = curatedText,
                    references = extractReferenceCandidates(subagentRun.referencesUsedJson)
                )
            }
    }

    private fun buildSynthesisSources(
        userId: UUID,
        orderedSources: List<Source>,
        subagentRuns: List<SubagentRun>
    ): List<Source> {
        val sourcesById = linkedMapOf<UUID, Source>()
        orderedSources.forEach { sourcesById[it.id] = it }

        val additionalSourceIds = linkedSetOf<UUID>()
        subagentRuns
            .filter { it.status == SubagentRunStatus.SUCCEEDED }
            .forEach { run ->
                extractSourceIds(run.sourceIdsUsedJson)
                    .filterNot { sourcesById.containsKey(it) }
                    .forEach { additionalSourceIds += it }
            }

        if (additionalSourceIds.isEmpty()) {
            return orderedSources
        }

        val loadedSourcesById = sourceRepository.findAllByUserIdAndIdIn(userId, additionalSourceIds)
            .filter { it.status == SourceStatus.ACTIVE }
            .associateBy { it.id }

        additionalSourceIds.forEach { sourceId ->
            loadedSourcesById[sourceId]?.let { sourcesById[sourceId] = it }
        }

        return sourcesById.values.toList()
    }

    private fun extractReferenceCandidates(referencesUsedJson: String?): List<BriefingReferenceCandidate> {
        if (referencesUsedJson.isNullOrBlank()) {
            return emptyList()
        }

        val root = runCatching { objectMapper.readTree(referencesUsedJson) }.getOrNull()
            ?: return emptyList()
        if (!root.isArray) {
            return emptyList()
        }

        val deduped = linkedMapOf<String, BriefingReferenceCandidate>()
        root.forEach { node ->
            val url = node.path("url").asText().trim()
            if (url.isBlank()) {
                return@forEach
            }
            val key = url.lowercase()
            if (deduped.containsKey(key)) {
                return@forEach
            }

            val title = node.path("title").asText().trim().ifBlank { url }
            val snippet = node.path("snippet").let { snippetNode ->
                if (snippetNode.isMissingNode || snippetNode.isNull) null
                else snippetNode.asText().trim().takeIf { it.isNotBlank() }
            }

            deduped[key] = BriefingReferenceCandidate(
                url = url,
                title = title,
                snippet = snippet
            )
        }

        return deduped.values.toList()
    }

    private fun extractSourceIds(sourceIdsUsedJson: String?): List<UUID> {
        if (sourceIdsUsedJson.isNullOrBlank()) {
            return emptyList()
        }

        val root = runCatching { objectMapper.readTree(sourceIdsUsedJson) }.getOrNull()
            ?: return emptyList()
        if (!root.isArray) {
            return emptyList()
        }

        return root.mapNotNull { node ->
            runCatching { UUID.fromString(node.asText().trim()) }.getOrNull()
        }
    }

    private fun nextEventId(): UUID = idGenerator.newId()

    private fun isRunPastDeadline(run: BriefingRun, now: Instant = Instant.now()): Boolean {
        val deadline = run.deadlineAt ?: run.createdAt.plusSeconds(executionConfig.globalTimeoutSeconds)
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

    private fun computeRetryDelaySeconds(result: SubagentExecutionResult.Failed, attempt: Int): Long {
        val retryConfig = executionConfig.retry
        if (result.retryAfterSeconds != null) {
            return result.retryAfterSeconds.coerceAtLeast(1L)
        }
        if (result.errorCode == ERROR_HTTP_429) {
            return when (attempt) {
                1 -> retryConfig.http429FallbackFirstSeconds
                else -> retryConfig.http429FallbackSecondSeconds
            }
        }
        return when (attempt) {
            1 -> retryConfig.transientDelayFirstSeconds
            else -> retryConfig.transientDelaySecondSeconds
        }
    }

    companion object {
        private const val ERROR_HTTP_429 = "http_429"
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
    val citationSources: List<Source> = emptyList(),
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
            generationResult: BriefingGenerationResult,
            citationSources: List<Source>
        ): ExecutionOrchestrationOutcome {
            return ExecutionOrchestrationOutcome(
                status = Status.SUCCEEDED,
                briefingRunId = briefingRunId,
                generationResult = generationResult,
                citationSources = citationSources,
                failureCode = null,
                failureMessage = null
            )
        }

        fun failed(
            briefingRunId: UUID? = null,
            failureCode: BriefingRunFailureCode,
            failureMessage: String
        ): ExecutionOrchestrationOutcome {
            return ExecutionOrchestrationOutcome(
                status = Status.FAILED,
                briefingRunId = briefingRunId,
                generationResult = null,
                failureCode = failureCode,
                failureMessage = failureMessage
            )
        }
    }
}
