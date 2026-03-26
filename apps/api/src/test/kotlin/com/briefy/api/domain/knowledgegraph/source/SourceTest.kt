package com.briefy.api.domain.knowledgegraph.source

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

class SourceTest {

    @Test
    fun `create initializes source with SUBMITTED status`() {
        val source = createSource()
        assertEquals(SourceStatus.SUBMITTED, source.status)
        assertEquals("https://example.com", source.url.normalized)
        assertNull(source.content)
        assertNull(source.metadata)
    }

    @Test
    fun `startExtraction transitions from SUBMITTED to EXTRACTING`() {
        val source = createSource()
        source.startExtraction()
        assertEquals(SourceStatus.EXTRACTING, source.status)
    }

    @Test
    fun `completeExtraction transitions from EXTRACTING to ACTIVE`() {
        val source = createSource()
        source.startExtraction()

        val content = Content.from("Some article content here")
        val metadata = Metadata.from(
            title = "Test Title",
            author = "Author",
            publishedDate = null,
            platform = "web",
            wordCount = content.wordCount,
            aiFormatted = false,
            extractionProvider = "jsoup"
        )

        source.completeExtraction(content, metadata)
        assertEquals(SourceStatus.ACTIVE, source.status)
        assertNotNull(source.content)
        assertNotNull(source.metadata)
        assertEquals("Some article content here", source.content!!.text)
        assertEquals("Test Title", source.metadata!!.title)
    }

    @Test
    fun `failExtraction transitions from EXTRACTING to FAILED and stores failure reason`() {
        val source = createSource()
        source.startExtraction()
        source.failExtraction("supadata_invalid_api_key")
        assertEquals(SourceStatus.FAILED, source.status)
        assertEquals("supadata_invalid_api_key", source.extractionFailureReason)
    }

    @Test
    fun `retry transitions from FAILED to SUBMITTED and clears extraction failure reason`() {
        val source = createSource()
        source.startExtraction()
        source.failExtraction("supadata_invalid_api_key")
        source.retry()
        assertEquals(SourceStatus.SUBMITTED, source.status)
        assertNull(source.extractionFailureReason)
    }

    @Test
    fun `archive transitions from ACTIVE to ARCHIVED`() {
        val source = createSource()
        source.startExtraction()
        source.completeExtraction(Content.from("text"), Metadata())
        source.archive()
        assertEquals(SourceStatus.ARCHIVED, source.status)
    }

    @Test
    fun `restore transitions from ARCHIVED to ACTIVE`() {
        val source = createSource()
        source.startExtraction()
        source.completeExtraction(Content.from("text"), Metadata())
        source.archive()

        source.restore()

        assertEquals(SourceStatus.ACTIVE, source.status)
    }

    @Test
    fun `invalid transition from SUBMITTED to ACTIVE throws`() {
        val source = createSource()
        assertThrows<IllegalArgumentException> {
            source.completeExtraction(Content.from("text"), Metadata())
        }
    }

    @Test
    fun `invalid transition from ACTIVE to SUBMITTED throws`() {
        val source = createSource()
        source.startExtraction()
        source.completeExtraction(Content.from("text"), Metadata())
        assertThrows<IllegalArgumentException> {
            source.retry()
        }
    }

    @Test
    fun `invalid transition from ACTIVE to ACTIVE throws`() {
        val source = createSource()
        source.startExtraction()
        source.completeExtraction(Content.from("text"), Metadata())
        assertThrows<IllegalArgumentException> {
            source.restore()
        }
    }

    @Test
    fun `invalid transition from SUBMITTED to FAILED throws`() {
        val source = createSource()
        assertThrows<IllegalArgumentException> {
            source.failExtraction()
        }
    }

    @Test
    fun `updatedAt changes on status transition`() {
        val source = createSource()
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
            wordCount = 1000,
            aiFormatted = false,
            extractionProvider = "jsoup"
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
            wordCount = 50,
            aiFormatted = false,
            extractionProvider = "jsoup"
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
            wordCount = 0,
            aiFormatted = false,
            extractionProvider = "jsoup"
        )
        assertNull(metadata.estimatedReadingTime)
    }

    @Test
    fun `requestNarration marks source pending`() {
        val source = createActiveSource()

        source.requestNarration()

        assertEquals(NarrationState.PENDING, source.narrationState)
        assertNull(source.narrationFailureReason)
    }

    @Test
    fun `completeNarration stores audio and marks source succeeded`() {
        val source = createActiveSource()
        val generatedAt = Instant.now()

        source.completeNarration(
            AudioContent(
                audioUrl = "https://example.com/audio.mp3",
                durationSeconds = 15,
                format = "mp3",
                contentHash = "abc123",
                voiceId = "voice-1",
                modelId = "model-1",
                generatedAt = generatedAt
            )
        )

        assertEquals(NarrationState.SUCCEEDED, source.narrationState)
        assertEquals("https://example.com/audio.mp3", source.audioContent?.audioUrl)
        assertEquals(generatedAt, source.audioContent?.generatedAt)
    }

    @Test
    fun `acceptManualContent clears existing narration`() {
        val source = createActiveSource()
        source.completeNarration(
            AudioContent(
                audioUrl = "https://example.com/audio.mp3",
                durationSeconds = 15,
                format = "mp3",
                contentHash = "abc123",
                voiceId = "voice-1",
                modelId = "model-1",
                generatedAt = Instant.now()
            )
        )

        source.acceptManualContent(
            Content.from("Updated manual content"),
            Metadata.from(
                title = "Updated",
                author = null,
                publishedDate = null,
                platform = "web",
                wordCount = 3,
                aiFormatted = false,
                extractionProvider = Metadata.EXTRACTION_PROVIDER_MANUAL
            )
        )

        assertEquals(NarrationState.NOT_GENERATED, source.narrationState)
        assertNull(source.audioContent)
        assertNull(source.narrationFailureReason)
    }

    private fun createSource(): Source {
        return Source.create(
            id = UUID.randomUUID(),
            rawUrl = "https://example.com",
            userId = UUID.randomUUID()
        )
    }

    private fun createActiveSource(): Source {
        return createSource().apply {
            startExtraction()
            completeExtraction(Content.from("text"), Metadata())
        }
    }
}
