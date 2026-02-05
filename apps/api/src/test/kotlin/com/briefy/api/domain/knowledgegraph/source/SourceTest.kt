package com.briefy.api.domain.knowledgegraph.source

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class SourceTest {

    @Test
    fun `create initializes source with SUBMITTED status`() {
        val source = Source.create("https://example.com")
        assertEquals(SourceStatus.SUBMITTED, source.status)
        assertEquals("https://example.com", source.url.normalized)
        assertNull(source.content)
        assertNull(source.metadata)
    }

    @Test
    fun `startExtraction transitions from SUBMITTED to EXTRACTING`() {
        val source = Source.create("https://example.com")
        source.startExtraction()
        assertEquals(SourceStatus.EXTRACTING, source.status)
    }

    @Test
    fun `completeExtraction transitions from EXTRACTING to ACTIVE`() {
        val source = Source.create("https://example.com")
        source.startExtraction()

        val content = Content.from("Some article content here")
        val metadata = Metadata.from(
            title = "Test Title",
            author = "Author",
            publishedDate = null,
            platform = "web",
            wordCount = content.wordCount
        )

        source.completeExtraction(content, metadata)
        assertEquals(SourceStatus.ACTIVE, source.status)
        assertNotNull(source.content)
        assertNotNull(source.metadata)
        assertEquals("Some article content here", source.content!!.text)
        assertEquals("Test Title", source.metadata!!.title)
    }

    @Test
    fun `failExtraction transitions from EXTRACTING to FAILED`() {
        val source = Source.create("https://example.com")
        source.startExtraction()
        source.failExtraction()
        assertEquals(SourceStatus.FAILED, source.status)
    }

    @Test
    fun `retry transitions from FAILED to SUBMITTED`() {
        val source = Source.create("https://example.com")
        source.startExtraction()
        source.failExtraction()
        source.retry()
        assertEquals(SourceStatus.SUBMITTED, source.status)
    }

    @Test
    fun `archive transitions from ACTIVE to ARCHIVED`() {
        val source = Source.create("https://example.com")
        source.startExtraction()
        source.completeExtraction(Content.from("text"), Metadata())
        source.archive()
        assertEquals(SourceStatus.ARCHIVED, source.status)
    }

    @Test
    fun `invalid transition from SUBMITTED to ACTIVE throws`() {
        val source = Source.create("https://example.com")
        assertThrows<IllegalArgumentException> {
            source.completeExtraction(Content.from("text"), Metadata())
        }
    }

    @Test
    fun `invalid transition from ACTIVE to SUBMITTED throws`() {
        val source = Source.create("https://example.com")
        source.startExtraction()
        source.completeExtraction(Content.from("text"), Metadata())
        assertThrows<IllegalArgumentException> {
            source.retry()
        }
    }

    @Test
    fun `invalid transition from SUBMITTED to FAILED throws`() {
        val source = Source.create("https://example.com")
        assertThrows<IllegalArgumentException> {
            source.failExtraction()
        }
    }

    @Test
    fun `updatedAt changes on status transition`() {
        val source = Source.create("https://example.com")
        val initialUpdatedAt = source.updatedAt
        // Small delay to ensure timestamp changes
        Thread.sleep(10)
        source.startExtraction()
        assertTrue(source.updatedAt >= initialUpdatedAt)
    }

    @Test
    fun `metadata estimatedReadingTime is calculated from word count`() {
        val metadata = Metadata.from(
            title = "Test",
            author = null,
            publishedDate = null,
            platform = "web",
            wordCount = 1000
        )
        assertEquals(5, metadata.estimatedReadingTime) // 1000 / 200 = 5
    }

    @Test
    fun `metadata estimatedReadingTime is at least 1 minute for short content`() {
        val metadata = Metadata.from(
            title = "Test",
            author = null,
            publishedDate = null,
            platform = "web",
            wordCount = 50
        )
        assertEquals(1, metadata.estimatedReadingTime)
    }

    @Test
    fun `metadata estimatedReadingTime is null for zero word count`() {
        val metadata = Metadata.from(
            title = "Test",
            author = null,
            publishedDate = null,
            platform = "web",
            wordCount = 0
        )
        assertNull(metadata.estimatedReadingTime)
    }
}
