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
                SubagentRunStatus.PENDING, SubagentRunStatus.RUNNING -> {
                    executeSubagent(
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

                SubagentRunStatus.RETRY_WAIT -> {
                    return ExecutionOrchestrationOutcome.failed(
                        failureCode = BriefingRunFailureCode.ORCHESTRATION_ERROR,
                        failureMessage = "retry_wait is not supported in deterministic execution mode"
                    )
                }
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
        subagentRun: SubagentRun,
        step: BriefingPlanStep,
        briefing: Briefing,
        orderedSources: List<Source>
    ) {
        val now = Instant.now()
        if (step.status == BriefingPlanStepStatus.PLANNED) {
            step.markRunning(now)
        }

        if (subagentRun.status == SubagentRunStatus.PENDING) {
            executionStateTransitionService.dispatchSubagentRun(
                subagentRunId = subagentRun.id,
                eventId = nextEventId(),
                occurredAt = now
            )
        }

        val result = subagentExecutionRunner.execute(
            SubagentExecutionContext(
                briefingId = briefing.id,
                briefingRunId = subagentRun.briefingRunId,
                subagentRunId = subagentRun.id,
                personaKey = subagentRun.personaKey,
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
                    subagentRunId = subagentRun.id,
                    eventId = nextEventId(),
                    curatedText = result.curatedText,
                    sourceIdsUsedJson = result.sourceIdsUsedJson,
                    referencesUsedJson = result.referencesUsedJson,
                    toolStatsJson = result.toolStatsJson,
                    occurredAt = occurredAt
                )
                step.markSucceeded(occurredAt)
            }

            SubagentExecutionResult.EmptyOutput -> {
                executionStateTransitionService.markSubagentCompletedEmpty(
                    subagentRunId = subagentRun.id,
                    eventId = nextEventId(),
                    occurredAt = occurredAt
                )
                step.markFailed(occurredAt)
            }

            is SubagentExecutionResult.Failed -> {
                executionStateTransitionService.markSubagentNonRetryableFailed(
                    subagentRunId = subagentRun.id,
                    eventId = nextEventId(),
                    errorCode = result.errorCode,
                    errorMessage = result.errorMessage,
                    occurredAt = occurredAt
                )
                step.markFailed(occurredAt)
            }
        }
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

        val run = try {
            briefingRunRepository.save(
                BriefingRun(
                    id = idGenerator.newId(),
                    briefingId = briefing.id,
                    executionFingerprint = fingerprint,
                    status = BriefingRunStatus.QUEUED,
                    createdAt = now,
                    updatedAt = now,
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

    companion object {
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
