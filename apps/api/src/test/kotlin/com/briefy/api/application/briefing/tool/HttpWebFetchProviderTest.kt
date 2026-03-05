package com.briefy.api.application.briefing.tool

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class HttpWebFetchProviderTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun provider(maxBodyBytes: Int = 100_000) = HttpWebFetchProvider(
        timeoutMs = 5000,
        maxBodyBytes = maxBodyBytes,
        ssrfCheckEnabled = false
    )

    @Test
    fun `fetches and extracts readable content from HTML`() {
        server.enqueue(
            MockResponse()
                .setBody("""
                    <html>
                    <head><title>Test Page</title></head>
                    <body>
                        <nav>Skip nav</nav>
                        <article><p>Important content here.</p></article>
                        <footer>Skip footer</footer>
                    </body>
                    </html>
                """.trimIndent())
                .addHeader("Content-Type", "text/html")
        )

        val result = provider().fetch(server.url("/page").toString())

        assertTrue(result is ToolResult.Success)
        val response = (result as ToolResult.Success).data
        assertEquals("Test Page", response.title)
        assertTrue(response.content.contains("Important content"))
        assertFalse(response.content.contains("Skip nav"))
    }

    @Test
    fun `rejects content exceeding max body bytes`() {
        val largeBody = "x".repeat(1000)
        server.enqueue(MockResponse().setBody(largeBody))

        val result = provider(maxBodyBytes = 500).fetch(server.url("/big").toString())

        assertTrue(result is ToolResult.Error)
        assertEquals(ToolErrorCode.CONTENT_TOO_LARGE, (result as ToolResult.Error).code)
    }

    @Test
    fun `maps 429 to HTTP_429 error`() {
        server.enqueue(MockResponse().setResponseCode(429))

        val result = provider().fetch(server.url("/rate-limited").toString())

        assertTrue(result is ToolResult.Error)
        assertEquals(ToolErrorCode.HTTP_429, (result as ToolResult.Error).code)
    }

    @Test
    fun `maps 500 to HTTP_5XX error`() {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = provider().fetch(server.url("/error").toString())

        assertTrue(result is ToolResult.Error)
        assertEquals(ToolErrorCode.HTTP_5XX, (result as ToolResult.Error).code)
    }

    @Test
    fun `handles plain text response`() {
        server.enqueue(
            MockResponse()
                .setBody("Just plain text content")
                .addHeader("Content-Type", "text/plain")
        )

        val result = provider().fetch(server.url("/text").toString())

        assertTrue(result is ToolResult.Success)
        val response = (result as ToolResult.Success).data
        assertTrue(response.content.contains("Just plain text content"))
    }

    @Test
    fun `SSRF blocks localhost when check enabled`() {
        val provider = HttpWebFetchProvider(ssrfCheckEnabled = true)
        val result = provider.fetch("http://localhost:9999/admin")

        assertTrue(result is ToolResult.Error)
        assertEquals(ToolErrorCode.SSRF_BLOCKED, (result as ToolResult.Error).code)
    }

    @Test
    fun `SSRF blocks private IP when check enabled`() {
        val provider = HttpWebFetchProvider(ssrfCheckEnabled = true)
        val result = provider.fetch("http://10.0.0.1/internal")

        assertTrue(result is ToolResult.Error)
        assertEquals(ToolErrorCode.SSRF_BLOCKED, (result as ToolResult.Error).code)
    }

    @Test
    fun `SSRF blocks cloud metadata when check enabled`() {
        val provider = HttpWebFetchProvider(ssrfCheckEnabled = true)
        val result = provider.fetch("http://169.254.169.254/latest/meta-data/")

        assertTrue(result is ToolResult.Error)
        assertEquals(ToolErrorCode.SSRF_BLOCKED, (result as ToolResult.Error).code)
    }

    @Test
    fun `SSRF blocks file scheme when check enabled`() {
        val provider = HttpWebFetchProvider(ssrfCheckEnabled = true)
        val result = provider.fetch("file:///etc/passwd")

        assertTrue(result is ToolResult.Error)
        assertEquals(ToolErrorCode.SSRF_BLOCKED, (result as ToolResult.Error).code)
    }
}
