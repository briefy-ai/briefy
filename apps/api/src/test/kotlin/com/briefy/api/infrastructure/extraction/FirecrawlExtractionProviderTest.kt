package com.briefy.api.infrastructure.extraction

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.RestClient

class FirecrawlExtractionProviderTest {
    @Test
    fun `extract returns markdown and aiFormatted false`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "success": true,
                      "data": {
                        "markdown": "# Hello world",
                        "metadata": {
                          "title": "Example title"
                        }
                      }
                    }
                    """.trimIndent()
                )
        )
        server.start()

        try {
            val provider = FirecrawlExtractionProvider(
                restClient = RestClient.builder().baseUrl(server.url("/").toString()).build(),
                apiKey = "fc-test",
                waitForMs = 1000
            )

            val result = provider.extract("https://example.com")

            val request = server.takeRequest()
            assertEquals("/v2/scrape", request.path)
            assertEquals("Bearer fc-test", request.getHeader("Authorization"))
            assertEquals("# Hello world", result.text)
            assertEquals("Example title", result.title)
            assertFalse(result.aiFormatted)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `extract throws when markdown missing`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"success": true, "data": {"markdown": ""}}""")
        )
        server.start()

        try {
            val provider = FirecrawlExtractionProvider(
                restClient = RestClient.builder().baseUrl(server.url("/").toString()).build(),
                apiKey = "fc-test",
                waitForMs = 1000
            )

            assertThrows<ExtractionProviderException> {
                provider.extract("https://example.com")
            }
        } finally {
            server.shutdown()
        }
    }
}
