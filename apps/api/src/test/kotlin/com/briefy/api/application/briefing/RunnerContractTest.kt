package com.briefy.api.application.briefing

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RunnerContractTest {

    @Test
    fun `Failed result carries retryable flag and retryAfterSeconds`() {
        val result = SubagentExecutionResult.Failed(
            errorCode = "http_429",
            errorMessage = "Rate limited",
            retryable = true,
            retryAfterSeconds = 5L
        )
        assertTrue(result.retryable)
        assertEquals(5L, result.retryAfterSeconds)
        assertEquals("http_429", result.errorCode)
    }

    @Test
    fun `Failed result defaults to non-retryable with no retryAfter`() {
        val result = SubagentExecutionResult.Failed(
            errorCode = "auth_failure",
            errorMessage = "Unauthorized"
        )
        assertFalse(result.retryable)
        assertNull(result.retryAfterSeconds)
    }

    @Test
    fun `Succeeded result is unchanged`() {
        val result = SubagentExecutionResult.Succeeded(
            curatedText = "test",
            sourceIdsUsedJson = "[\"id1\"]"
        )
        assertEquals("test", result.curatedText)
    }
}
