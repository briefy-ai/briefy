package com.briefy.api.application.briefing.tool

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class BraveWebSearchProviderTest {

    private lateinit var server: MockWebServer
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `parses search results from Brave response`() {
        server.enqueue(
            MockResponse()
                .setBody("""
                    {
                        "web": {
                            "results": [
                                {"title": "Result 1", "url": "https://a.com", "description": "Snippet 1"},
                                {"title": "Result 2", "url": "https://b.com", "description": "Snippet 2"}
                            ]
                        }
                    }
                """.trimIndent())
                .addHeader("Content-Type", "application/json")
        )

        val provider = BraveWebSearchProvider(
            apiKey = "test-key",
            objectMapper = objectMapper,
            baseUrl = server.url("/").toString().trimEnd('/'),
            timeoutMs = 5000
        )
        val result = provider.search("test query", 5)

        assertTrue(result is ToolResult.Success)
        val response = (result as ToolResult.Success).data
        assertEquals("test query", response.query)
        assertEquals(2, response.results.size)
        assertEquals("Result 1", response.results[0].title)
        assertEquals("https://a.com", response.results[0].url)
    }

    @Test
    fun `sends API key in request header`() {
        server.enqueue(
            MockResponse()
                .setBody("""{"web":{"results":[]}}""")
                .addHeader("Content-Type", "application/json")
        )

        val provider = BraveWebSearchProvider(
            apiKey = "my-secret-key",
            objectMapper = objectMapper,
            baseUrl = server.url("/").toString().trimEnd('/'),
            timeoutMs = 5000
        )
        provider.search("test", 3)

        val request = server.takeRequest()
        assertEquals("my-secret-key", request.getHeader("X-Subscription-Token"))
    }

    @Test
    fun `maps 429 to HTTP_429`() {
        server.enqueue(MockResponse().setResponseCode(429))

        val provider = BraveWebSearchProvider(
            apiKey = "key",
            objectMapper = objectMapper,
            baseUrl = server.url("/").toString().trimEnd('/'),
            timeoutMs = 5000
        )
        val result = provider.search("query", 5)

        assertTrue(result is ToolResult.Error)
        assertEquals(ToolErrorCode.HTTP_429, (result as ToolResult.Error).code)
    }

    @Test
    fun `maps 401 to PROVIDER_AUTH_ERROR`() {
        server.enqueue(MockResponse().setResponseCode(401))

        val provider = BraveWebSearchProvider(
            apiKey = "bad-key",
            objectMapper = objectMapper,
            baseUrl = server.url("/").toString().trimEnd('/'),
            timeoutMs = 5000
        )
        val result = provider.search("query", 5)

        assertTrue(result is ToolResult.Error)
        assertEquals(ToolErrorCode.PROVIDER_AUTH_ERROR, (result as ToolResult.Error).code)
    }

    @Test
    fun `maps 500 to HTTP_5XX`() {
        server.enqueue(MockResponse().setResponseCode(500))

        val provider = BraveWebSearchProvider(
            apiKey = "key",
            objectMapper = objectMapper,
            baseUrl = server.url("/").toString().trimEnd('/'),
            timeoutMs = 5000
        )
        val result = provider.search("query", 5)

        assertTrue(result is ToolResult.Error)
        assertEquals(ToolErrorCode.HTTP_5XX, (result as ToolResult.Error).code)
    }

    @Test
    fun `handles empty results gracefully`() {
        server.enqueue(
            MockResponse()
                .setBody("""{"web":{"results":[]}}""")
                .addHeader("Content-Type", "application/json")
        )

        val provider = BraveWebSearchProvider(
            apiKey = "key",
            objectMapper = objectMapper,
            baseUrl = server.url("/").toString().trimEnd('/'),
            timeoutMs = 5000
        )
        val result = provider.search("obscure query", 5)

        assertTrue(result is ToolResult.Success)
        assertTrue((result as ToolResult.Success).data.results.isEmpty())
    }

    @Test
    fun `handles missing web field gracefully`() {
        server.enqueue(
            MockResponse()
                .setBody("""{}""")
                .addHeader("Content-Type", "application/json")
        )

        val provider = BraveWebSearchProvider(
            apiKey = "key",
            objectMapper = objectMapper,
            baseUrl = server.url("/").toString().trimEnd('/'),
            timeoutMs = 5000
        )
        val result = provider.search("query", 5)

        assertTrue(result is ToolResult.Success)
        assertTrue((result as ToolResult.Success).data.results.isEmpty())
    }
}
