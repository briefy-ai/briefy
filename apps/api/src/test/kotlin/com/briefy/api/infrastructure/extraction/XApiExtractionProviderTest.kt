package com.briefy.api.infrastructure.extraction

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.client.RestClient

class XApiExtractionProviderTest {
    @Test
    fun `extract returns single post markdown`() {
        val server = MockWebServer()
        server.enqueue(
            jsonResponse(
                """
                {
                  "data": [{
                    "id": "123",
                    "text": "hello from x",
                    "author_id": "42",
                    "created_at": "2026-02-12T10:00:00Z"
                  }],
                  "includes": {
                    "users": [{"id":"42","name":"Alice","username":"alice"}]
                  }
                }
                """.trimIndent()
            )
        )
        server.start()

        try {
            val provider = newProvider(server)
            val result = provider.extract("https://x.com/alice/status/123")

            val request = server.takeRequest()
            assertTrue(request.path!!.startsWith("/2/tweets"))
            assertEquals("Bearer token-1", request.getHeader("Authorization"))
            assertTrue(result.text.contains("# X Post"))
            assertTrue(result.text.contains("hello from x"))
            assertEquals("Alice (@alice)", result.author)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `extract prefers note tweet text`() {
        val server = MockWebServer()
        server.enqueue(
            jsonResponse(
                """
                {
                  "data": [{
                    "id": "123",
                    "text": "short",
                    "author_id": "42",
                    "note_tweet": {"text":"long note tweet body"}
                  }]
                }
                """.trimIndent()
            )
        )
        server.start()

        try {
            val provider = newProvider(server)
            val result = provider.extract("https://x.com/alice/status/123")

            assertTrue(result.text.contains("long note tweet body"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `extract returns thread markdown with author-only posts sorted by created_at`() {
        val server = MockWebServer()
        server.enqueue(
            jsonResponse(
                """
                {
                  "data": [{
                    "id": "123",
                    "text": "root",
                    "author_id": "42",
                    "conversation_id": "999",
                    "created_at": "2026-02-12T10:00:00Z"
                  }],
                  "includes": {
                    "users": [{"id":"42","name":"Alice","username":"alice"}]
                  }
                }
                """.trimIndent()
            )
        )
        server.enqueue(
            jsonResponse(
                """
                {
                  "data": [
                    {"id":"c","text":"third","author_id":"42","created_at":"2026-02-12T10:03:00Z"},
                    {"id":"b","text":"other user","author_id":"77","created_at":"2026-02-12T10:02:00Z"},
                    {"id":"a","text":"first","author_id":"42","created_at":"2026-02-12T10:01:00Z"}
                  ]
                }
                """.trimIndent()
            )
        )
        server.start()

        try {
            val provider = newProvider(server)
            val result = provider.extract("https://twitter.com/alice/status/123")

            val secondReq = server.takeRequest()
            val threadReq = server.takeRequest()
            assertTrue(secondReq.path!!.startsWith("/2/tweets"))
            assertTrue(threadReq.path!!.startsWith("/2/tweets/search/recent"))

            assertTrue(result.text.contains("# X Thread"))
            val firstIdx = result.text.indexOf("first")
            val thirdIdx = result.text.indexOf("third")
            assertTrue(firstIdx in 0 until thirdIdx)
            assertTrue(!result.text.contains("other user"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `extract detects article and performs refetch`() {
        val server = MockWebServer()
        server.enqueue(
            jsonResponse(
                """
                {
                  "data": [{
                    "id": "123",
                    "text": "root",
                    "author_id": "42",
                    "article": {"id":"art-1"},
                    "created_at": "2026-02-12T10:00:00Z"
                  }],
                  "includes": {
                    "users": [{"id":"42","name":"Alice","username":"alice"}]
                  }
                }
                """.trimIndent()
            )
        )
        server.enqueue(
            jsonResponse(
                """
                {
                  "data": {
                    "id": "123",
                    "article": {"id":"art-1","title":"Article Title","text":"Article body"}
                  }
                }
                """.trimIndent()
            )
        )
        server.start()

        try {
            val provider = newProvider(server)
            val result = provider.extract("https://x.com/alice/status/123")

            val first = server.takeRequest()
            val second = server.takeRequest()
            assertTrue(first.path!!.contains("ids=123"))
            assertEquals("/2/tweets/123?tweet.fields=article", second.path)
            assertTrue(result.text.contains("Article body"))
            assertEquals("Article Title", result.title)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `extract article uses plain_text when present`() {
        val server = MockWebServer()
        server.enqueue(
            jsonResponse(
                """
                {
                  "data": [{
                    "id": "2022050269262151783",
                    "text": "https://t.co/short",
                    "author_id": "15540222",
                    "article": {"title":"On APIs"},
                    "created_at": "2026-02-12T10:00:00Z"
                  }],
                  "includes": {
                    "users": [{"id":"15540222","name":"Guillermo Rauch","username":"rauchg"}]
                  }
                }
                """.trimIndent()
            )
        )
        server.enqueue(
            jsonResponse(
                """
                {
                  "data": {
                    "id": "2022050269262151783",
                    "article": {
                      "title":"On APIs",
                      "preview_text":"preview only",
                      "plain_text":"Long article body from plain_text"
                    }
                  }
                }
                """.trimIndent()
            )
        )
        server.start()

        try {
            val provider = newProvider(server)
            val result = provider.extract("https://x.com/rauchg/status/2022050269262151783?s=20")

            assertTrue(result.text.contains("Long article body from plain_text"))
            assertTrue(!result.text.contains("https://t.co/short"))
            assertEquals("On APIs", result.title)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `extract throws unsupported for non-status url`() {
        val server = MockWebServer()
        server.start()
        try {
            val provider = newProvider(server)
            assertThrows<ExtractionProviderException> {
                provider.extract("https://x.com/home")
            }
        } finally {
            server.shutdown()
        }
    }

    private fun newProvider(server: MockWebServer): XApiExtractionProvider {
        return XApiExtractionProvider(
            restClient = RestClient.builder().baseUrl(server.url("/").toString()).build(),
            bearerToken = "token-1",
            timeoutMs = 10_000,
            threadMaxResults = 100
        )
    }

    private fun jsonResponse(body: String): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)
    }
}
