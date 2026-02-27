package com.briefy.api.infrastructure.extraction

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.RestClient

class FirecrawlAgentExtractionProviderTest {
    private val objectMapper = ObjectMapper()

    @Test
    fun `extract returns article text when agent completes`() {
        val server = MockWebServer()
        server.enqueue(jsonResponse("""{"success":true,"id":"job-123"}"""))
        server.enqueue(jsonResponse("""{"success":true,"status":"processing"}"""))
        server.enqueue(
            jsonResponse(
                """
                {
                  "success": true,
                  "status": "completed",
                  "data": {
                    "output": {
                      "posthog_article_text": "PostHog article body"
                    }
                  }
                }
                """.trimIndent()
            )
        )
        server.start()

        try {
            val provider = createProvider(server, pollIntervalMs = 1, maxWaitMs = 1000)
            val result = provider.extract("https://posthog.com/blog/forward-deployed-engineer")

            val startRequest = server.takeRequest()
            val startBody = startRequest.body.readUtf8()
            assertEquals("/v2/agent", startRequest.path)
            assertEquals("Bearer fc-test", startRequest.getHeader("Authorization"))
            assertTrue(startBody.contains("\"model\":\"spark-1-mini\""))
            assertTrue(startBody.contains("posthog_article_text"))

            val pollRequest1 = server.takeRequest()
            val pollRequest2 = server.takeRequest()
            assertEquals("/v2/agent/job-123", pollRequest1.path)
            assertEquals("/v2/agent/job-123", pollRequest2.path)
            assertEquals("PostHog article body", result.text)
            assertFalse(result.aiFormatted)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `extract throws when agent fails`() {
        val server = MockWebServer()
        server.enqueue(jsonResponse("""{"success":true,"id":"job-123"}"""))
        server.enqueue(jsonResponse("""{"success":true,"status":"failed","message":"agent failed"}"""))
        server.start()

        try {
            val provider = createProvider(server, pollIntervalMs = 1, maxWaitMs = 1000)
            val exception = assertThrows<ExtractionProviderException> {
                provider.extract("https://posthog.com/blog/forward-deployed-engineer")
            }
            assertTrue(exception.message!!.contains("agent failed"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `extract throws when agent does not complete before timeout`() {
        val server = MockWebServer()
        server.enqueue(jsonResponse("""{"success":true,"id":"job-123"}"""))
        server.enqueue(jsonResponse("""{"success":true,"status":"processing"}"""))
        server.start()

        try {
            val provider = createProvider(server, pollIntervalMs = 20, maxWaitMs = 10)
            val exception = assertThrows<ExtractionProviderException> {
                provider.extract("https://posthog.com/blog/forward-deployed-engineer")
            }
            assertEquals(ExtractionFailureReason.TIMEOUT, exception.reason)
        } finally {
            server.shutdown()
        }
    }

    private fun createProvider(
        server: MockWebServer,
        pollIntervalMs: Long,
        maxWaitMs: Long
    ): FirecrawlAgentExtractionProvider {
        return FirecrawlAgentExtractionProvider(
            restClient = RestClient.builder().baseUrl(server.url("/").toString()).build(),
            objectMapper = objectMapper,
            apiKey = "fc-test",
            model = "spark-1-mini",
            pollIntervalMs = pollIntervalMs,
            maxWaitMs = maxWaitMs,
            maxCredits = null
        )
    }

    private fun jsonResponse(body: String): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)
    }
}
