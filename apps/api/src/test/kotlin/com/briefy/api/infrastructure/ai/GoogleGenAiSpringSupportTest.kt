package com.briefy.api.infrastructure.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.MessageType
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.google.genai.GoogleGenAiChatOptions

class GoogleGenAiSpringSupportTest {
    @Test
    fun `buildPrompt uses provider-specific options and keeps system message separate`() {
        val prompt = GoogleGenAiSpringSupport.buildPrompt(
            prompt = "user prompt",
            systemPrompt = "system prompt",
            model = "gemini-2.5-flash"
        )

        assertEquals(2, prompt.instructions.size)
        assertEquals(MessageType.SYSTEM, prompt.instructions[0].messageType)
        assertEquals("system prompt", prompt.instructions[0].text)
        assertEquals(MessageType.USER, prompt.instructions[1].messageType)
        assertEquals("user prompt", prompt.instructions[1].text)

        val options = prompt.options as GoogleGenAiChatOptions
        assertEquals("gemini-2.5-flash", options.model)
    }

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
