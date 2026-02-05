package com.briefy.api.domain.knowledgegraph.source

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UrlTest {

    @Test
    fun `normalize adds https when no protocol`() {
        assertEquals("https://example.com", Url.normalize("example.com"))
    }

    @Test
    fun `normalize upgrades http to https`() {
        assertEquals("https://example.com", Url.normalize("http://example.com"))
    }

    @Test
    fun `normalize removes www prefix`() {
        assertEquals("https://example.com", Url.normalize("https://www.example.com"))
    }

    @Test
    fun `normalize removes trailing slash`() {
        assertEquals("https://example.com", Url.normalize("https://example.com/"))
    }

    @Test
    fun `normalize lowercases host`() {
        assertEquals("https://example.com/Path", Url.normalize("https://EXAMPLE.COM/Path"))
    }

    @Test
    fun `normalize removes fragment`() {
        assertEquals("https://example.com/page", Url.normalize("https://example.com/page#section"))
    }

    @Test
    fun `normalize preserves query parameters`() {
        assertEquals("https://example.com/page?q=test", Url.normalize("https://example.com/page?q=test"))
    }

    @Test
    fun `normalize preserves path`() {
        assertEquals("https://example.com/blog/post-1", Url.normalize("https://www.example.com/blog/post-1/"))
    }

    @Test
    fun `normalize trims whitespace`() {
        assertEquals("https://example.com", Url.normalize("  example.com  "))
    }

    @Test
    fun `normalize rejects blank url`() {
        assertThrows<Exception> {
            Url.normalize("")
        }
    }

    @Test
    fun `detectPlatform identifies youtube`() {
        assertEquals("youtube", Url.detectPlatform("https://youtube.com/watch?v=123"))
        assertEquals("youtube", Url.detectPlatform("https://youtu.be/123"))
    }

    @Test
    fun `detectPlatform identifies twitter`() {
        assertEquals("twitter", Url.detectPlatform("https://twitter.com/user/status/123"))
        assertEquals("twitter", Url.detectPlatform("https://x.com/user/status/123"))
    }

    @Test
    fun `detectPlatform identifies reddit`() {
        assertEquals("reddit", Url.detectPlatform("https://reddit.com/r/kotlin"))
    }

    @Test
    fun `detectPlatform identifies medium`() {
        assertEquals("medium", Url.detectPlatform("https://medium.com/@user/article"))
    }

    @Test
    fun `detectPlatform identifies github`() {
        assertEquals("github", Url.detectPlatform("https://github.com/user/repo"))
    }

    @Test
    fun `detectPlatform identifies arxiv`() {
        assertEquals("arxiv", Url.detectPlatform("https://arxiv.org/abs/2301.00001"))
    }

    @Test
    fun `detectPlatform identifies wikipedia`() {
        assertEquals("wikipedia", Url.detectPlatform("https://en.wikipedia.org/wiki/Kotlin"))
    }

    @Test
    fun `detectPlatform returns web for unknown hosts`() {
        assertEquals("web", Url.detectPlatform("https://example.com/article"))
    }

    @Test
    fun `from creates Url with normalized values`() {
        val url = Url.from("http://www.Example.com/blog/post/")
        assertEquals("http://www.Example.com/blog/post/", url.raw)
        assertEquals("https://example.com/blog/post", url.normalized)
        assertEquals("web", url.platform)
    }

    @Test
    fun `from creates Url with correct platform`() {
        val url = Url.from("https://www.youtube.com/watch?v=abc123")
        assertEquals("youtube", url.platform)
    }

    @Test
    fun `from rejects blank url`() {
        assertThrows<IllegalArgumentException> {
            Url.from("")
        }
    }

    @Test
    fun `isValid returns true for valid urls`() {
        assertTrue(Url.isValid("https://example.com"))
        assertTrue(Url.isValid("example.com"))
        assertTrue(Url.isValid("http://www.example.com/path"))
    }

    @Test
    fun `isValid returns false for invalid urls`() {
        assertFalse(Url.isValid(""))
        assertFalse(Url.isValid("   "))
    }
}
