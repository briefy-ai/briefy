package com.briefy.api.infrastructure.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation

class GoogleGenAiSpringSupportTest {
    @Test
    fun `extractText joins multiple text parts from the first candidate only`() {
        val response = ChatResponse(
            listOf(
                generation(text = "first part", candidateIndex = 0),
                generation(text = "second part", candidateIndex = 0),
                generation(text = "ignored candidate", candidateIndex = 1)
            )
        )

        assertEquals("first part\nsecond part", GoogleGenAiSpringSupport.extractText(response))
    }

    @Test
    fun `extractText falls back to ordered non blank outputs when candidate metadata is absent`() {
        val response = ChatResponse(
            listOf(
                generation(text = "first"),
                generation(text = "  "),
                generation(text = "second")
            )
        )

        assertEquals("first\nsecond", GoogleGenAiSpringSupport.extractText(response))
    }

    @Test
    fun `unwrapFailure preserves the root cause message for retry heuristics`() {
        val cause = RuntimeException("429 too many requests")
        val wrapped = RuntimeException("Failed to generate content", cause)

        val unwrapped = GoogleGenAiSpringSupport.unwrapFailure(wrapped)

        assertTrue(unwrapped.message.orEmpty().contains("429"))
        assertSame(cause, unwrapped.cause)
    }

    @Test
    fun `unwrapFailure keeps a root exception without creating an extra wrapper`() {
        val error = RuntimeException()

        val unwrapped = GoogleGenAiSpringSupport.unwrapFailure(error)

        assertSame(error, unwrapped)
    }

    private fun generation(text: String, candidateIndex: Int? = null): Generation {
        val properties = candidateIndex?.let { mapOf("candidateIndex" to it) } ?: emptyMap()
        return Generation(
            AssistantMessage.builder()
                .content(text)
                .properties(properties)
                .build(),
            ChatGenerationMetadata.builder()
                .metadata("finishReason", "stop")
                .build()
        )
    }
}
