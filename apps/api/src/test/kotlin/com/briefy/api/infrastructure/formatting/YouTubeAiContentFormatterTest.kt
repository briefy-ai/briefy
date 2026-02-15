package com.briefy.api.infrastructure.formatting

import com.briefy.api.infrastructure.ai.AiAdapter
import com.briefy.api.infrastructure.extraction.ExtractionProviderId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class YouTubeAiContentFormatterTest {
    private val aiAdapter: AiAdapter = mock()
    private val formatter = YouTubeAiContentFormatter(aiAdapter)

    @Test
    fun `supports youtube extractor only`() {
        assertTrue(formatter.supports(ExtractionProviderId.YOUTUBE))
        assertFalse(formatter.supports(ExtractionProviderId.JSOUP))
        assertFalse(formatter.supports(ExtractionProviderId.FIRECRAWL))
    }

    @Test
    fun `uses provided provider and model`() {
        whenever(aiAdapter.complete(any(), any(), any(), anyOrNull())).thenReturn("formatted")

        val result = formatter.format("raw captions text", "zhipuai", "glm-4.7")

        assertEquals("formatted", result)
        verify(aiAdapter).complete(
            provider = eq("zhipuai"),
            model = eq("glm-4.7"),
            prompt = any(),
            systemPrompt = eq(null)
        )
    }

    @Test
    fun `rejects blank content`() {
        assertThrows<IllegalArgumentException> {
            formatter.format("   ", "google_genai", "gemini-2.5-flash-lite")
        }
    }
}
