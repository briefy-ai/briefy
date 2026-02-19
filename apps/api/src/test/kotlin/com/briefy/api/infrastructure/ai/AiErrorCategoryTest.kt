package com.briefy.api.infrastructure.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.SocketTimeoutException

class AiErrorCategoryTest {
    @Test
    fun `maps illegal argument to validation`() {
        assertEquals(AiErrorCategory.VALIDATION, AiErrorCategory.from(IllegalArgumentException("bad request")))
    }

    @Test
    fun `maps timeout exceptions to timeout`() {
        assertEquals(AiErrorCategory.TIMEOUT, AiErrorCategory.from(SocketTimeoutException("timed out")))
    }

    @Test
    fun `maps provider outage messages to provider unavailable`() {
        assertEquals(
            AiErrorCategory.PROVIDER_UNAVAILABLE,
            AiErrorCategory.from(RuntimeException("503 Service Unavailable"))
        )
    }

    @Test
    fun `maps unknown errors to unknown`() {
        assertEquals(AiErrorCategory.UNKNOWN, AiErrorCategory.from(RuntimeException("boom")))
    }
}
