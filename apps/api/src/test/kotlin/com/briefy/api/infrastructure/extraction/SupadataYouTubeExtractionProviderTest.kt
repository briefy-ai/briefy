package com.briefy.api.infrastructure.extraction

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.util.concurrent.TimeUnit

class SupadataYouTubeExtractionProviderTest {
    private val fallbackProvider: ExtractionProvider = mock()
    private val url = "https://youtu.be/dQw4w9WgXcQ"

    @Test
    fun `extract returns supadata transcript and oembed metadata`() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "content": "Native transcript text",
                      "lang": "en",
                      "availableLangs": ["en", "es"]
                    }
                    """.trimIndent()
                )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "title": "Video title",
                      "author_name": "Channel name",
                      "thumbnail_url": "https://img.youtube.com/test.jpg"
                    }
                    """.trimIndent()
                )
        )
        server.start()

        try {
            val provider = createProvider(server)

            val result = provider.extract(url)

            val transcriptRequest = server.takeRequest(1, TimeUnit.SECONDS)
                ?: error("Expected Supadata transcript request")
            assertEquals("test-key", transcriptRequest.getHeader("x-api-key"))
            assertEquals("GET", transcriptRequest.method)
            assertEquals(true, transcriptRequest.path?.startsWith("/v1/transcript?"))
            assertEquals(true, transcriptRequest.path?.contains("text=true"))
            assertEquals(true, transcriptRequest.path?.contains("mode=native"))
            assertEquals(true, transcriptRequest.path?.contains("lang=en"))

            val oEmbedRequest = server.takeRequest(1, TimeUnit.SECONDS)
                ?: error("Expected YouTube oEmbed request")
            assertEquals("GET", oEmbedRequest.method)
            assertEquals(true, oEmbedRequest.path?.startsWith("/oembed?"))
            assertEquals(true, oEmbedRequest.path?.contains("format=json"))

            assertEquals("Native transcript text", result.text)
            assertEquals("Video title", result.title)
            assertEquals("Channel name", result.author)
            assertEquals("https://img.youtube.com/test.jpg", result.ogImageUrl)
            assertEquals("dQw4w9WgXcQ", result.videoId)
            assertEquals("https://www.youtube.com/embed/dQw4w9WgXcQ", result.videoEmbedUrl)
            assertEquals("supadata", result.transcriptSource)
            assertEquals("en", result.transcriptLanguage)
            assertFalse(result.aiFormatted)
            verify(fallbackProvider, never()).extract(url)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `extract falls back to youtube provider when supadata returns partial transcript`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(206))
        server.start()

        val fallbackResult = ExtractionResult(
            text = "Fallback transcript",
            title = "Fallback title",
            author = "Fallback author",
            publishedDate = null
        )
        whenever(fallbackProvider.extract(url)).thenReturn(fallbackResult)

        try {
            val provider = createProvider(server)

            val result = provider.extract(url)

            assertSame(fallbackResult, result)
            verify(fallbackProvider).extract(url)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `extract throws invalid api key error when supadata returns unauthorized`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401))
        server.start()

        try {
            val provider = createProvider(server)

            val exception = assertThrows<ExtractionProviderException> {
                provider.extract(url)
            }

            assertEquals(ExtractionProviderId.SUPADATA_YOUTUBE, exception.providerId)
            assertEquals(ExtractionFailureReason.BLOCKED, exception.reason)
            assertEquals("supadata_invalid_api_key", exception.message)
            verify(fallbackProvider, never()).extract(url)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `extract throws rate limited error when supadata returns too many requests`() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(429))
        server.start()

        try {
            val provider = createProvider(server)

            val exception = assertThrows<ExtractionProviderException> {
                provider.extract(url)
            }

            assertEquals(ExtractionProviderId.SUPADATA_YOUTUBE, exception.providerId)
            assertEquals(ExtractionFailureReason.BLOCKED, exception.reason)
            assertEquals("supadata_rate_limited", exception.message)
            verify(fallbackProvider, never()).extract(url)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `extract falls back to youtube provider when supadata request fails`() {
        val server = MockWebServer()
        server.start()
        val baseUrl = server.url("/").toString()
        server.shutdown()

        val fallbackResult = ExtractionResult(
            text = "Fallback transcript",
            title = "Fallback title",
            author = "Fallback author",
            publishedDate = null
        )
        whenever(fallbackProvider.extract(url)).thenReturn(fallbackResult)

        val provider = SupadataYouTubeExtractionProvider(
            restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(250)
                    setReadTimeout(250)
                })
                .build(),
            apiKey = "test-key",
            fallbackProvider = fallbackProvider,
            timeoutMs = 2_000,
            oEmbedBaseUrl = baseUrl
        )

        val result = provider.extract(url)

        assertSame(fallbackResult, result)
        verify(fallbackProvider).extract(url)
    }

    private fun createProvider(server: MockWebServer): SupadataYouTubeExtractionProvider {
        return SupadataYouTubeExtractionProvider(
            restClient = RestClient.builder().baseUrl(server.url("/").toString()).build(),
            apiKey = "test-key",
            fallbackProvider = fallbackProvider,
            timeoutMs = 5_000,
            oEmbedBaseUrl = server.url("/").toString()
        )
    }
}
