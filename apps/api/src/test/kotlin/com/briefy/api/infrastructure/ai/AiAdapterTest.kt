package com.briefy.api.infrastructure.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.metadata.ChatGenerationMetadata
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.DefaultChatOptions
import org.springframework.web.client.RestClient

class AiAdapterTest {
    private val aiAdapter = AiAdapter(
        restClientBuilder = RestClient.builder(),
        aiCallObserver = mock(),
        zhipuChatApiKey = "",
        zhipuChatBaseUrl = "https://api.z.ai/api/paas",
        zhipuDefaultModel = "glm-4.7-flash",
        minimaxChatApiKey = "",
        minimaxChatBaseUrl = "https://api.minimax.chat",
        minimaxDefaultModel = "MiniMax-M2.5",
        googleGenAiApiKey = "",
        googleGenAiDefaultModel = "gemini-2.5-flash"
    )

    @Test
    fun `buildPromptSpec applies shared Spring options and system prompt`() {
        val requestSpec = mock<ChatClient.ChatClientRequestSpec>()
        whenever(requestSpec.system(any<String>())).thenReturn(requestSpec)
        whenever(requestSpec.options(any<DefaultChatOptions>())).thenReturn(requestSpec)
        whenever(requestSpec.user(any<String>())).thenReturn(requestSpec)

        val result = aiAdapter.buildPromptSpec(
            requestSpec = requestSpec,
            prompt = "user prompt",
            systemPrompt = "system prompt",
            model = "gemini-2.5-flash"
        )
        val optionsCaptor = argumentCaptor<DefaultChatOptions>()

        assertSame(requestSpec, result)
        verify(requestSpec).system("system prompt")
        verify(requestSpec).options(optionsCaptor.capture())
        verify(requestSpec).user("user prompt")
        assertEquals("gemini-2.5-flash", optionsCaptor.firstValue.model)
    }

    @Test
    fun `buildPromptSpec skips system message when absent`() {
        val requestSpec = mock<ChatClient.ChatClientRequestSpec>()
        whenever(requestSpec.options(any<DefaultChatOptions>())).thenReturn(requestSpec)
        whenever(requestSpec.user(any<String>())).thenReturn(requestSpec)

        aiAdapter.buildPromptSpec(
            requestSpec = requestSpec,
            prompt = "user prompt",
            systemPrompt = null,
            model = "gemini-2.5-flash"
        )
        val optionsCaptor = argumentCaptor<DefaultChatOptions>()

        verify(requestSpec, never()).system(any<String>())
        verify(requestSpec).options(optionsCaptor.capture())
        verify(requestSpec).user("user prompt")
        assertEquals("gemini-2.5-flash", optionsCaptor.firstValue.model)
    }

    @Test
    fun `extractSpringResponse joins Google candidate zero parts`() {
        val responseSpec = mock<ChatClient.CallResponseSpec>()
        whenever(responseSpec.chatResponse()).thenReturn(
            ChatResponse(
                listOf(
                    generation(text = "first", candidateIndex = 0),
                    generation(text = "second", candidateIndex = 0),
                    generation(text = "ignored", candidateIndex = 1)
                )
            )
        )

        val result = aiAdapter.extractSpringResponse("google_genai", responseSpec)

        assertEquals("first\nsecond", result)
        verify(responseSpec).chatResponse()
        verify(responseSpec, never()).content()
    }

    @Test
    fun `extractSpringResponse trims content for non Google providers`() {
        val responseSpec = mock<ChatClient.CallResponseSpec>()
        whenever(responseSpec.content()).thenReturn("  hello world  ")

        val result = aiAdapter.extractSpringResponse("zhipuai", responseSpec)

        assertEquals("hello world", result)
        verify(responseSpec).content()
        verify(responseSpec, never()).chatResponse()
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
