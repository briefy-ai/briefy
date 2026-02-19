package com.briefy.api.infrastructure.ai

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiPayloadSanitizerTest {
    private val sanitizer = AiPayloadSanitizer()

    @Test
    fun `redacts secret-like values`() {
        val input = "Authorization: Bearer abcdefghijklmnop token=abc1234567890 sk-lf-abcdefghijklmnop"

        val sanitized = sanitizer.sanitize(input, maxChars = 500)

        assertFalse(sanitized.contains("abcdefghijklmnop"))
        assertFalse(sanitized.contains("abc1234567890"))
        assertTrue(sanitized.contains("[REDACTED]"))
    }

    @Test
    fun `truncates payload with marker`() {
        val sanitized = sanitizer.sanitize("abcdefghijklmnopqrstuvwxyz", maxChars = 10)

        assertTrue(sanitized.startsWith("abcdefghij"))
        assertTrue(sanitized.endsWith("...[truncated]"))
    }
}
