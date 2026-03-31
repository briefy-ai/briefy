package com.briefy.api.application.briefing

import com.briefy.api.domain.knowledgegraph.briefing.*
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.infrastructure.ai.AiPayloadSanitizer
import com.briefy.api.infrastructure.id.IdGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import io.opentelemetry.api.OpenTelemetry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant
import java.util.*

class BriefingExecutionOrchestratorServiceTest {

    private val briefingRunRepository = mock<BriefingRunRepository>()
    private val subagentRunRepository = mock<SubagentRunRepository>()
    private val synthesisRunRepository = mock<SynthesisRunRepository>()
    private val briefingPlanStepRepository = mock<BriefingPlanStepRepository>()
    private val executionStateTransitionService = mock<ExecutionStateTransitionService>()
    private val subagentExecutionRunner = mock<SubagentExecutionRunner>()
    private val synthesisExecutionRunner = mock<SynthesisExecutionRunner>()
    private val executionFingerprintService = mock<ExecutionFingerprintService>()
    private val sourceRepository = mock<SourceRepository>()
    private val idGenerator = mock<IdGenerator>()
    private val objectMapper = ObjectMapper()

    private val executionConfig = ExecutionConfigProperties(
        globalTimeoutSeconds = 180L,
        subagentTimeoutSeconds = 90L,
        maxAttempts = 3,
        retry = ExecutionConfigProperties.RetryConfig(
            transientDelayFirstSeconds = 1L,
            transientDelaySecondSeconds = 2L
        )
    )

    private lateinit var service: BriefingExecutionOrchestratorService

    private val farFuture = Instant.now().plusSeconds(3600)
    private val briefingId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val briefingRunId = UUID.randomUUID()
    private val subagentRunId = UUID.randomUUID()
    private val synthesisRunId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        whenever(idGenerator.newId()).thenReturn(UUID.randomUUID())

        service = BriefingExecutionOrchestratorService(
            briefingRunRepository = briefingRunRepository,
            subagentRunRepository = subagentRunRepository,
            synthesisRunRepository = synthesisRunRepository,
            briefingPlanStepRepository = briefingPlanStepRepository,
            executionStateTransitionService = executionStateTransitionService,
            subagentExecutionRunner = subagentExecutionRunner,
            synthesisExecutionRunner = synthesisExecutionRunner,
            executionFingerprintService = executionFingerprintService,
            executionConfig = executionConfig,
            sourceRepository = sourceRepository,
            idGenerator = idGenerator,
            objectMapper = objectMapper,
            tracer = OpenTelemetry.noop().getTracer("test"),
            sanitizer = AiPayloadSanitizer()
        )
    }

    @Test
    fun `EmptyOutput triggers retry when attempts not exhausted`() {
        val briefing = Briefing(
            id = briefingId, userId = userId,
            enrichmentIntent = BriefingEnrichmentIntent.DEEP_DIVE,
            status = BriefingStatus.GENERATING
        )
        val planStep = BriefingPlanStep(
            id = UUID.randomUUID(), briefingId = briefingId,
            personaId = null, personaName = "Market Analyst",
            stepOrder = 1, task = "Analyze market"
        )

        val briefingRun = BriefingRun(
            id = briefingRunId, briefingId = briefingId,
            executionFingerprint = "fp", status = BriefingRunStatus.RUNNING,
            totalPersonas = 1, requiredForSynthesis = 1,
            deadlineAt = farFuture
        )

        val subagentRunAttempt1 = SubagentRun(
            id = subagentRunId, briefingRunId = briefingRunId, briefingId = briefingId,
            personaKey = "step-1", status = SubagentRunStatus.PENDING,
            attempt = 1, maxAttempts = 3,
            deadlineAt = farFuture
        )
        val subagentRunAfterRetry = SubagentRun(
            id = subagentRunId, briefingRunId = briefingRunId, briefingId = briefingId,
            personaKey = "step-1", status = SubagentRunStatus.RUNNING,
            attempt = 2, maxAttempts = 3,
            deadlineAt = farFuture
        )
        val subagentRunSucceeded = SubagentRun(
            id = subagentRunId, briefingRunId = briefingRunId, briefingId = briefingId,
            personaKey = "step-1", status = SubagentRunStatus.SUCCEEDED,
            attempt = 2, maxAttempts = 3,
            curatedText = "Analysis output"
        )

        val synthesisRun = SynthesisRun(
            id = synthesisRunId, briefingRunId = briefingRunId,
            status = SynthesisRunStatus.NOT_STARTED
        )

        // Bootstrap: no active run → create new
        whenever(briefingRunRepository.findTopByBriefingIdAndStatusInOrderByCreatedAtDesc(eq(briefingId), any()))
            .thenReturn(null)
        whenever(executionFingerprintService.compute(any(), any(), any())).thenReturn("fp")
        whenever(briefingRunRepository.save(any<BriefingRun>())).thenReturn(briefingRun)
        whenever(subagentRunRepository.saveAll(any<Iterable<SubagentRun>>())).thenReturn(listOf(subagentRunAttempt1))
        whenever(synthesisRunRepository.save(any<SynthesisRun>())).thenReturn(synthesisRun)

        // executeSubagent loop: briefingRun lookups (deadline/cancellation checks)
        whenever(briefingRunRepository.findById(briefingRunId)).thenReturn(Optional.of(briefingRun))

        // subagentRun.findById returns different states across iterations:
        // Iter 1: PENDING(1) → PENDING(1) for dispatch + deadline set → runner executes
        // Iter 2: RUNNING(2) after retry → RUNNING(2) for deadline set → runner executes
        whenever(subagentRunRepository.findById(subagentRunId))
            .thenReturn(Optional.of(subagentRunAttempt1))    // iter1: terminal check
            .thenReturn(Optional.of(subagentRunAttempt1))    // iter1: deadline set
            .thenReturn(Optional.of(subagentRunAfterRetry))  // iter2: terminal check
            .thenReturn(Optional.of(subagentRunAfterRetry))  // iter2: deadline set

        // Runner: EmptyOutput on attempt 1, Succeeded on attempt 2
        whenever(subagentExecutionRunner.execute(any()))
            .thenReturn(SubagentExecutionResult.EmptyOutput)
            .thenReturn(SubagentExecutionResult.Succeeded(
                curatedText = "Analysis output",
                sourceIdsUsedJson = "[]",
                toolStatsJson = """{"budgetExhausted":false}"""
            ))

        // Post-fan-out: refresh runs for synthesis gate
        whenever(subagentRunRepository.findByBriefingRunIdOrderByCreatedAtAsc(briefingRunId))
            .thenReturn(listOf(subagentRunSucceeded))
        whenever(synthesisRunRepository.findByBriefingRunId(briefingRunId)).thenReturn(synthesisRun)

        // Synthesis
        whenever(synthesisExecutionRunner.run(any())).thenReturn(
            BriefingGenerationResult(
                markdownBody = "# Synthesis",
                references = emptyList(),
                conflictHighlights = emptyList()
            )
        )

        val outcome = service.executeApprovedBriefing(briefing, emptyList(), listOf(planStep))

        assertEquals(ExecutionOrchestrationOutcome.Status.SUCCEEDED, outcome.status)

        // Verify retry path was taken
        verify(executionStateTransitionService).markSubagentTransientFailedToRetryWait(
            subagentRunId = eq(subagentRunId),
            eventId = any(),
            errorCode = eq("empty_output"),
            errorMessage = eq("AI produced no usable output"),
            occurredAt = any()
        )
        // Verify markSubagentCompletedEmpty was NOT called (retry, not terminal)
        verify(executionStateTransitionService, never()).markSubagentCompletedEmpty(any(), any(), anyOrNull(), any())
        // Verify runner was called twice (attempt 1 + retry attempt 2)
        verify(subagentExecutionRunner, times(2)).execute(any())
    }

    @Test
    fun `EmptyOutput terminates with markSubagentCompletedEmpty when attempts exhausted`() {
        val briefing = Briefing(
            id = briefingId, userId = userId,
            enrichmentIntent = BriefingEnrichmentIntent.DEEP_DIVE,
            status = BriefingStatus.GENERATING
        )
        val planStep = BriefingPlanStep(
            id = UUID.randomUUID(), briefingId = briefingId,
            personaId = null, personaName = "Market Analyst",
            stepOrder = 1, task = "Analyze market"
        )

        val briefingRun = BriefingRun(
            id = briefingRunId, briefingId = briefingId,
            executionFingerprint = "fp", status = BriefingRunStatus.RUNNING,
            totalPersonas = 1, requiredForSynthesis = 1,
            deadlineAt = farFuture
        )

        // Already on final attempt
        val subagentRunFinalAttempt = SubagentRun(
            id = subagentRunId, briefingRunId = briefingRunId, briefingId = briefingId,
            personaKey = "step-1", status = SubagentRunStatus.RUNNING,
            attempt = 3, maxAttempts = 3,
            deadlineAt = farFuture
        )
        val subagentRunTerminal = SubagentRun(
            id = subagentRunId, briefingRunId = briefingRunId, briefingId = briefingId,
            personaKey = "step-1", status = SubagentRunStatus.SKIPPED_NO_OUTPUT,
            attempt = 3, maxAttempts = 3
        )

        val synthesisRun = SynthesisRun(
            id = synthesisRunId, briefingRunId = briefingRunId,
            status = SynthesisRunStatus.NOT_STARTED
        )

        whenever(briefingRunRepository.findTopByBriefingIdAndStatusInOrderByCreatedAtDesc(eq(briefingId), any()))
            .thenReturn(null)
        whenever(executionFingerprintService.compute(any(), any(), any())).thenReturn("fp")
        whenever(briefingRunRepository.save(any<BriefingRun>())).thenReturn(briefingRun)
        whenever(subagentRunRepository.saveAll(any<Iterable<SubagentRun>>())).thenReturn(listOf(subagentRunFinalAttempt))
        whenever(synthesisRunRepository.save(any<SynthesisRun>())).thenReturn(synthesisRun)

        whenever(briefingRunRepository.findById(briefingRunId)).thenReturn(Optional.of(briefingRun))

        // Final attempt: RUNNING(3) → execute → EmptyOutput → exhausted
        whenever(subagentRunRepository.findById(subagentRunId))
            .thenReturn(Optional.of(subagentRunFinalAttempt))  // terminal check
            .thenReturn(Optional.of(subagentRunFinalAttempt))  // deadline set

        whenever(subagentExecutionRunner.execute(any()))
            .thenReturn(SubagentExecutionResult.EmptyOutput)

        // Post-fan-out refresh
        whenever(subagentRunRepository.findByBriefingRunIdOrderByCreatedAtAsc(briefingRunId))
            .thenReturn(listOf(subagentRunTerminal))
        whenever(synthesisRunRepository.findByBriefingRunId(briefingRunId)).thenReturn(synthesisRun)

        val outcome = service.executeApprovedBriefing(briefing, emptyList(), listOf(planStep))

        // Should fail because synthesis gate not met (0 succeeded < 1 required)
        assertEquals(ExecutionOrchestrationOutcome.Status.FAILED, outcome.status)

        // Verify exhausted path: markSubagentCompletedEmpty WAS called
        verify(executionStateTransitionService).markSubagentCompletedEmpty(eq(subagentRunId), any(), anyOrNull(), any())
        // Verify retry path was NOT taken
        verify(executionStateTransitionService, never()).markSubagentTransientFailedToRetryWait(
            any(), any(), any(), anyOrNull(), any()
        )
        // Runner called exactly once (no retry)
        verify(subagentExecutionRunner, times(1)).execute(any())
    }
}
