package com.briefy.api.infrastructure.extraction

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
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
            assertTrue(request.path!!.contains("attachments.media_keys"))
            assertEquals("Bearer token-1", request.getHeader("Authorization"))
            assertTrue(result.text.contains("# X Post"))
            assertTrue(result.text.contains("hello from x"))
            assertTrue(!result.text.contains("Created:"))
            assertEquals("@alice", result.author)
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
    fun `extract returns thread markdown with self replies only and lean formatting`() {
        val server = MockWebServer()
        server.enqueue(
            jsonResponse(
                """
                {
                  "data": [{
                    "id": "999",
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
                    {"id":"c","text":"third","author_id":"42","in_reply_to_user_id":"42","created_at":"2026-02-12T10:03:00Z"},
                    {"id":"d","text":"reply to commenter","author_id":"42","in_reply_to_user_id":"77","created_at":"2026-02-12T10:04:00Z"},
                    {"id":"b","text":"other user","author_id":"77","created_at":"2026-02-12T10:02:00Z"},
                    {"id":"a","text":"first","author_id":"42","in_reply_to_user_id":"42","created_at":"2026-02-12T10:01:00Z"}
                  ]
                }
                """.trimIndent()
            )
        )
        server.start()

        try {
            val provider = newProvider(server)
            val result = provider.extract("https://twitter.com/alice/status/999")

            val secondReq = server.takeRequest()
            val threadReq = server.takeRequest()
            assertTrue(secondReq.path!!.startsWith("/2/tweets"))
            assertTrue(threadReq.path!!.startsWith("/2/tweets/search/recent"))

            assertTrue(result.text.contains("# X Thread"))
            val firstIdx = result.text.indexOf("first")
            val thirdIdx = result.text.indexOf("third")
            assertTrue(firstIdx in 0 until thirdIdx)
            assertTrue(!result.text.contains("other user"))
            assertTrue(!result.text.contains("reply to commenter"))
            assertTrue(!result.text.contains("## Post"))
            assertTrue(!result.text.contains("Created:"))
            assertTrue(result.text.contains("---"))
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
                    "article": {"id":"art-1","cover_media":"media-1"},
                    "created_at": "2026-02-12T10:00:00Z"
                  }],
                  "includes": {
                    "users": [{"id":"42","name":"Alice","username":"alice"}],
                    "media": [{"media_key":"media-1","type":"photo","url":"https://pbs.twimg.com/media/article-cover.jpg"}]
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
            assertEquals("https://pbs.twimg.com/media/article-cover.jpg", result.ogImageUrl)
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

    @Test
    fun `extract sets og image from attachment media`() {
        val server = MockWebServer()
        server.enqueue(
            jsonResponse(
                """
                {
                  "data": [{
                    "id": "123",
                    "text": "hello from x",
                    "author_id": "42",
                    "attachments": {"media_keys": ["media-1"]}
                  }],
                  "includes": {
                    "users": [{"id":"42","name":"Alice","username":"alice"}],
                    "media": [{"media_key":"media-1","type":"photo","url":"https://pbs.twimg.com/media/post-image.jpg"}]
                  }
                }
                """.trimIndent()
            )
        )
        server.start()

        try {
            val provider = newProvider(server)
            val result = provider.extract("https://x.com/alice/status/123")

            assertEquals("https://pbs.twimg.com/media/post-image.jpg", result.ogImageUrl)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `extract transcribes x video and returns caption plus transcript`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            jsonResponse(
                """
                {
                  "data": [{
                    "id": "123",
                    "text": "watch this clip",
                    "author_id": "42",
                    "created_at": "2026-02-12T10:00:00Z",
                    "attachments": {"media_keys": ["media-1"]}
                  }],
                  "includes": {
                    "users": [{"id":"42","name":"Alice","username":"alice"}],
                    "media": [{
                      "media_key":"media-1",
                      "type":"video",
                      "duration_ms": 12000,
                      "preview_image_url":"https://pbs.twimg.com/media/video-preview.jpg",
                      "variants":[
                        {"bit_rate":256000,"content_type":"video/mp4","url":"%s"},
                        {"bit_rate":832000,"content_type":"video/mp4","url":"%s"}
                      ]
                    }]
                  }
                }
                """.trimIndent().format(server.url("/video-low.mp4"), server.url("/video-high.mp4"))
            )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "video/mp4")
                .setBody("fake video bytes")
        )

        try {
            val transcriptionService: MediaWhisperTranscriptionService = mock()
            whenever(transcriptionService.transcribe(any(), any())).thenReturn("transcribed words")
            val provider = newProvider(server, transcriptionService)

            val result = provider.extract("https://x.com/alice/status/123")

            val apiRequest = server.takeRequest()
            val mediaRequest = server.takeRequest()
            assertTrue(apiRequest.path!!.startsWith("/2/tweets"))
            assertEquals("/video-high.mp4", mediaRequest.path)
            assertTrue(result.text.contains("# X Video"))
            assertTrue(result.text.contains("watch this clip"))
            assertTrue(result.text.contains("## Transcript"))
            assertTrue(result.text.contains("transcribed words"))
            assertEquals("watch this clip", result.title)
            assertEquals("whisper", result.transcriptSource)
            assertEquals(12, result.videoDurationSeconds)
            assertEquals("https://pbs.twimg.com/media/video-preview.jpg", result.ogImageUrl)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `extract retries lower bitrate mp4 when highest variant exceeds size limit`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            jsonResponse(
                """
                {
                  "data": [{
                    "id": "123",
                    "text": "watch this clip",
                    "author_id": "42",
                    "created_at": "2026-02-12T10:00:00Z",
                    "attachments": {"media_keys": ["media-1"]}
                  }],
                  "includes": {
                    "users": [{"id":"42","name":"Alice","username":"alice"}],
                    "media": [{
                      "media_key":"media-1",
                      "type":"video",
                      "duration_ms": 12000,
                      "variants":[
                        {"bit_rate":256000,"content_type":"video/mp4","url":"%s"},
                        {"bit_rate":832000,"content_type":"video/mp4","url":"%s"}
                      ]
                    }]
                  }
                }
                """.trimIndent().format(server.url("/video-low.mp4"), server.url("/video-high.mp4"))
            )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "video/mp4")
                .setBody("this body is definitely too large")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "video/mp4")
                .setBody("small")
        )

        try {
            val transcriptionService: MediaWhisperTranscriptionService = mock()
            whenever(transcriptionService.transcribe(any(), any())).thenReturn("transcribed words")
            val provider = newProvider(
                server = server,
                transcriptionService = transcriptionService,
                maxVideoDownloadBytes = 8
            )

            val result = provider.extract("https://x.com/alice/status/123")

            server.takeRequest()
            val firstVariantRequest = server.takeRequest()
            val secondVariantRequest = server.takeRequest()
            assertEquals("/video-high.mp4", firstVariantRequest.path)
            assertEquals("/video-low.mp4", secondVariantRequest.path)
            assertTrue(result.text.contains("# X Video"))
            assertTrue(result.text.contains("transcribed words"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `extract falls back to normal text when video has no downloadable mp4`() {
        val server = MockWebServer()
        server.start()
        server.enqueue(
            jsonResponse(
                """
                {
                  "data": [{
                    "id": "123",
                    "text": "watch this clip",
                    "author_id": "42",
                    "created_at": "2026-02-12T10:00:00Z",
                    "attachments": {"media_keys": ["media-1"]}
                  }],
                  "includes": {
                    "users": [{"id":"42","name":"Alice","username":"alice"}],
                    "media": [{
                      "media_key":"media-1",
                      "type":"video",
                      "duration_ms": 12000,
                      "variants":[
                        {"bit_rate":832000,"content_type":"application/x-mpegURL","url":"%s"}
                      ]
                    }]
                  }
                }
                """.trimIndent().format(server.url("/video.m3u8"))
            )
        )

        try {
            val transcriptionService: MediaWhisperTranscriptionService = mock()
            val provider = newProvider(server, transcriptionService)

            val result = provider.extract("https://x.com/alice/status/123")

            assertTrue(result.text.contains("# X Post"))
            assertTrue(result.text.contains("watch this clip"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `extract keeps non self reply as single post without thread lookup`() {
        val server = MockWebServer()
        server.enqueue(
            jsonResponse(
                """
                {
                  "data": [{
                    "id": "123",
                    "text": "replying to someone else",
                    "author_id": "42",
                    "conversation_id": "999",
                    "in_reply_to_user_id": "77",
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

            val apiRequest = server.takeRequest()
            assertTrue(apiRequest.path!!.startsWith("/2/tweets"))
            assertEquals(0, server.requestCount - 1)
            assertTrue(result.text.contains("# X Post"))
            assertTrue(result.text.contains("replying to someone else"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `extract excludes author replies to other users from thread results`() {
        val server = MockWebServer()
        server.enqueue(
            jsonResponse(
                """
                {
                  "data": [{
                    "id": "999",
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
                    {"id":"1000","text":"first self reply","author_id":"42","in_reply_to_user_id":"42","created_at":"2026-02-12T10:01:00Z"},
                    {"id":"1001","text":"replying to a commenter","author_id":"42","in_reply_to_user_id":"77","created_at":"2026-02-12T10:02:00Z"}
                  ]
                }
                """.trimIndent()
            )
        )
        server.start()

        try {
            val provider = newProvider(server)

            val result = provider.extract("https://x.com/alice/status/999")

            assertTrue(result.text.contains("first self reply"))
            assertTrue(!result.text.contains("replying to a commenter"))
        } finally {
            server.shutdown()
        }
    }

    private fun newProvider(
        server: MockWebServer,
        transcriptionService: MediaWhisperTranscriptionService = mock(),
        maxVideoDownloadBytes: Long = 10_000_000
    ): XApiExtractionProvider {
        return XApiExtractionProvider(
            restClient = RestClient.builder().baseUrl(server.url("/").toString()).build(),
            mediaWhisperTranscriptionService = transcriptionService,
            bearerToken = "token-1",
            timeoutMs = 10_000,
            threadMaxResults = 100,
            maxVideoDurationSeconds = 900,
            maxVideoDownloadBytes = maxVideoDownloadBytes,
            mediaDownloadTimeoutMs = 10_000
        )
    }

    private fun jsonResponse(body: String): MockResponse {
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(body)
    }
}
