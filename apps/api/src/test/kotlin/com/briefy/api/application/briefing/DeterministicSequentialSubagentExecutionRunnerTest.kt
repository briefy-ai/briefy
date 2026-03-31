package com.briefy.api.application.briefing

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class DeterministicSequentialSubagentExecutionRunnerTest {

    private val runner = DeterministicSequentialSubagentExecutionRunner(ObjectMapper())

    private fun context(task: String) = SubagentExecutionContext(
        briefingId = UUID.randomUUID(),
        briefingRunId = UUID.randomUUID(),
        subagentRunId = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        attempt = 1,
        maxAttempts = 3,
        personaKey = "step-1",
        personaName = "Test Persona",
        task = task,
        sources = listOf(
            BriefingSourceInput(
                sourceId = UUID.randomUUID(),
                title = "Test Source",
                url = "https://example.com",
                text = "Source text content"
            )
        )
    )

    @Test
    fun `transient timeout returns retryable failure`() {
        val result = runner.execute(context("[transient:timeout] analyze this"))
        assertTrue(result is SubagentExecutionResult.Failed)
        val failed = result as SubagentExecutionResult.Failed
        assertEquals("timeout", failed.errorCode)
        assertTrue(failed.retryable)
        assertNull(failed.retryAfterSeconds)
    }

    @Test
    fun `transient 429 with retry-after returns structured retryAfterSeconds`() {
        val result = runner.execute(context("[transient:429:15] analyze this"))
        assertTrue(result is SubagentExecutionResult.Failed)
        val failed = result as SubagentExecutionResult.Failed
        assertEquals("http_429", failed.errorCode)
        assertTrue(failed.retryable)
        assertEquals(15L, failed.retryAfterSeconds)
    }

    @Test
    fun `transient 429 without retry-after returns retryable with null retryAfterSeconds`() {
        val result = runner.execute(context("[transient:429] analyze this"))
        assertTrue(result is SubagentExecutionResult.Failed)
        val failed = result as SubagentExecutionResult.Failed
        assertEquals("http_429", failed.errorCode)
        assertTrue(failed.retryable)
        assertNull(failed.retryAfterSeconds)
    }

    @Test
    fun `transient 5xx returns retryable failure`() {
        val result = runner.execute(context("[transient:5xx] analyze this"))
        assertTrue(result is SubagentExecutionResult.Failed)
        val failed = result as SubagentExecutionResult.Failed
        assertEquals("http_5xx", failed.errorCode)
        assertTrue(failed.retryable)
    }

    @Test
    fun `transient network returns retryable failure`() {
        val result = runner.execute(context("[transient:network] analyze this"))
        assertTrue(result is SubagentExecutionResult.Failed)
        val failed = result as SubagentExecutionResult.Failed
        assertEquals("network_error", failed.errorCode)
        assertTrue(failed.retryable)
    }

    @Test
    fun `forced failure returns non-retryable`() {
        val result = runner.execute(context("[fail] analyze this"))
        assertTrue(result is SubagentExecutionResult.Failed)
        val failed = result as SubagentExecutionResult.Failed
        assertEquals("deterministic_failure", failed.errorCode)
        assertFalse(failed.retryable)
        assertNull(failed.retryAfterSeconds)
    }

    @Test
    fun `force_fail returns non-retryable`() {
        val result = runner.execute(context("force_fail analyze this"))
        assertTrue(result is SubagentExecutionResult.Failed)
        val failed = result as SubagentExecutionResult.Failed
        assertFalse(failed.retryable)
    }

    @Test
    fun `empty marker returns EmptyOutput`() {
        val result = runner.execute(context("[empty] analyze this"))
        assertTrue(result is SubagentExecutionResult.EmptyOutput)
    }

    @Test
    fun `normal task returns Succeeded with curated text`() {
        val result = runner.execute(context("Analyze source content"))
        assertTrue(result is SubagentExecutionResult.Succeeded)
        val succeeded = result as SubagentExecutionResult.Succeeded
        assertTrue(succeeded.curatedText.contains("Test Persona"))
        assertNotNull(succeeded.sourceIdsUsedJson)
        assertNotNull(succeeded.toolStatsJson)
    }
}
