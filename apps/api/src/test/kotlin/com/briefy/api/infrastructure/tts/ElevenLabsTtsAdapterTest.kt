package com.briefy.api.infrastructure.tts

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import java.util.concurrent.TimeUnit

class ElevenLabsTtsAdapterTest {

    @Test
    fun `synthesize posts text model and api key to ElevenLabs`() {
        val server = MockWebServer()
        server.start()
        try {
            val audioBytes = byteArrayOf(1, 2, 3, 4)
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(Buffer().write(audioBytes))
            )

            val adapter = ElevenLabsTtsAdapter(
                restClientBuilder = RestClient.builder(),
                properties = TtsProperties(
                    baseUrl = server.url("/").toString().removeSuffix("/"),
                    voiceId = "voice-123",
                    modelId = "model-1"
                ),
                objectMapper = jacksonObjectMapper()
            )

            val result = adapter.synthesize("hello narration", "secret-key")

            val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "No request received" }
            val body = request.body.readUtf8()
            assertEquals("/v1/text-to-speech/voice-123?output_format=mp3_44100_128", request.path)
            assertEquals("secret-key", request.getHeader("xi-api-key"))
            assertEquals(true, request.getHeader("Content-Type")?.startsWith("application/json") == true)
            assertEquals(true, body.contains("\"text\":\"hello narration\""))
            assertEquals(true, body.contains("\"model_id\":\"model-1\""))
            assertArrayEquals(audioBytes, result)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `synthesize maps paid plan required to non retryable provider error`() {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(402)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {"detail":{"type":"payment_required","code":"paid_plan_required","message":"Free users cannot use library voices via the API.","status":"payment_required"}}
                        """.trimIndent()
                    )
            )

            val adapter = ElevenLabsTtsAdapter(
                restClientBuilder = RestClient.builder(),
                properties = TtsProperties(
                    baseUrl = server.url("/").toString().removeSuffix("/"),
                    voiceId = "voice-123",
                    modelId = "model-1"
                ),
                objectMapper = jacksonObjectMapper()
            )

            val ex = assertThrows<ElevenLabsTtsException> {
                adapter.synthesize("hello narration", "secret-key")
            }

            assertEquals("paid_plan_required", ex.code)
            assertFalse(ex.retryable)
            assertEquals(
                "Your ElevenLabs API key cannot use the configured voice. Free ElevenLabs plans cannot use library voices via API.",
                ex.userMessage
            )
        } finally {
            server.shutdown()
        }
    }
}
