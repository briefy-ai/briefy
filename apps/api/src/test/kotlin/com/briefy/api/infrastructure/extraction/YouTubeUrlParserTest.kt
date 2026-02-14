package com.briefy.api.infrastructure.extraction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test

class YouTubeUrlParserTest {
    @Test
    fun `parses watch url`() {
        val ref = YouTubeUrlParser.parse("https://youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals("dQw4w9WgXcQ", ref.videoId)
        assertEquals("https://youtube.com/watch?v=dQw4w9WgXcQ", ref.canonicalUrl)
        assertEquals("https://www.youtube.com/embed/dQw4w9WgXcQ", ref.embedUrl)
    }

    @Test
    fun `parses short url`() {
        val ref = YouTubeUrlParser.parse("https://youtu.be/dQw4w9WgXcQ")
        assertEquals("dQw4w9WgXcQ", ref.videoId)
    }

    @Test
    fun `parses shorts url`() {
        val ref = YouTubeUrlParser.parse("https://youtube.com/shorts/dQw4w9WgXcQ")
        assertEquals("dQw4w9WgXcQ", ref.videoId)
    }

    @Test
    fun `rejects playlists`() {
        assertThrows<IllegalArgumentException> {
            YouTubeUrlParser.parse("https://youtube.com/watch?v=dQw4w9WgXcQ&list=PL123")
        }
    }
}
