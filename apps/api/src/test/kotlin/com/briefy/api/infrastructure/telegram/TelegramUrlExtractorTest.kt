package com.briefy.api.infrastructure.telegram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelegramUrlExtractorTest {
    private val extractor = TelegramUrlExtractor()

    @Test
    fun `extract should return urls and trim trailing punctuation`() {
        val result = extractor.extract(
            "Check https://example.com/article, and also www.youtube.com/watch?v=abc123!",
            maxUrls = 10
        )

        assertEquals(listOf("https://example.com/article", "www.youtube.com/watch?v=abc123"), result.urls)
        assertFalse(result.truncated)
        assertEquals(0, result.skippedCount)
    }

    @Test
    fun `extract should cap results and report skipped count`() {
        val text = (1..12).joinToString(" ") { "https://example.com/$it" }

        val result = extractor.extract(text, maxUrls = 10)

        assertEquals(10, result.urls.size)
        assertTrue(result.truncated)
        assertEquals(2, result.skippedCount)
    }
}
