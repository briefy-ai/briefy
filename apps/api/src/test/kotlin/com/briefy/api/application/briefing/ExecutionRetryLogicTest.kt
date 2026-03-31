package com.briefy.api.application.briefing

import com.briefy.api.infrastructure.id.IdGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import io.opentelemetry.api.OpenTelemetry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ExecutionRetryLogicTest {

    @Test
    fun `orchestrator accepts config properties for timeout and retry`() {
        val config = ExecutionConfigProperties(
            globalTimeoutSeconds = 300L,
            subagentTimeoutSeconds = 120L,
            maxAttempts = 5,
            retry = ExecutionConfigProperties.RetryConfig(
                transientDelayFirstSeconds = 2L,
                transientDelaySecondSeconds = 4L,
                http429FallbackFirstSeconds = 5L,
                http429FallbackSecondSeconds = 10L
            )
        )
        val orchestrator = BriefingExecutionOrchestratorService(
            briefingRunRepository = mock(),
            subagentRunRepository = mock(),
            synthesisRunRepository = mock(),
            briefingPlanStepRepository = mock(),
            executionStateTransitionService = mock(),
            subagentExecutionRunner = mock(),
            synthesisExecutionRunner = mock(),
            executionFingerprintService = mock(),
            executionConfig = config,
            sourceRepository = mock(),
            idGenerator = mock(),
            objectMapper = ObjectMapper(),
            tracer = OpenTelemetry.noop().getTracer("test")
        )
        assertNotNull(orchestrator)
        assertEquals(300L, config.globalTimeoutSeconds)
        assertEquals(5, config.maxAttempts)
        assertEquals(10L, config.retry.http429FallbackSecondSeconds)
    }

    @Test
    fun `non-retryable failure result carries retryable=false`() {
        val result = SubagentExecutionResult.Failed(
            errorCode = "deterministic_failure",
            errorMessage = "Hard failure",
            retryable = false
        )
        assertFalse(result.retryable)
        assertNull(result.retryAfterSeconds)
    }

    @Test
    fun `retryable failure with retryAfterSeconds carries structured value`() {
        val result = SubagentExecutionResult.Failed(
            errorCode = "http_429",
            errorMessage = "Rate limited",
            retryable = true,
            retryAfterSeconds = 10L
        )
        assertTrue(result.retryable)
        assertEquals(10L, result.retryAfterSeconds)
    }

    @Test
    fun `retryable failure without retryAfterSeconds defaults to null`() {
        val result = SubagentExecutionResult.Failed(
            errorCode = "timeout",
            errorMessage = "Connection timed out",
            retryable = true
        )
        assertTrue(result.retryable)
        assertNull(result.retryAfterSeconds)
    }

    @Test
    fun `config defaults match legacy hardcoded values`() {
        val config = ExecutionConfigProperties()
        assertEquals(180L, config.globalTimeoutSeconds)
        assertEquals(90L, config.subagentTimeoutSeconds)
        assertEquals(3, config.maxAttempts)
        assertEquals(ExecutionConfigProperties.SynthesisType.AI, config.synthesis)
        assertEquals(1L, config.retry.transientDelayFirstSeconds)
        assertEquals(2L, config.retry.transientDelaySecondSeconds)
        assertEquals(2L, config.retry.http429FallbackFirstSeconds)
        assertEquals(4L, config.retry.http429FallbackSecondSeconds)
    }
}
