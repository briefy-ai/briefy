package com.briefy.api.application.source

import com.briefy.api.application.settings.AiModelSelection
import com.briefy.api.application.settings.UserAiSettingsService
import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.SharedSourceSnapshot
import com.briefy.api.domain.knowledgegraph.source.SharedSourceSnapshotRepository
import com.briefy.api.domain.knowledgegraph.source.SharedSourceSnapshotStatus
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceType
import com.briefy.api.infrastructure.extraction.ExtractionProviderId
import com.briefy.api.infrastructure.formatting.ExtractionContentFormatter
import com.briefy.api.infrastructure.id.IdGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class SourceContentFormatterServiceTest {
    private val sourceRepository: SourceRepository = mock()
    private val sharedSourceSnapshotRepository: SharedSourceSnapshotRepository = mock()
    private val jsoupFormatter: ExtractionContentFormatter = mock()
    private val youtubeFormatter: ExtractionContentFormatter = mock()
    private val userAiSettingsService: UserAiSettingsService = mock()
    private val idGenerator: IdGenerator = mock()

    private val service = SourceContentFormatterService(
        sourceRepository = sourceRepository,
        sharedSourceSnapshotRepository = sharedSourceSnapshotRepository,
        extractionContentFormatters = listOf(jsoupFormatter, youtubeFormatter),
        userAiSettingsService = userAiSettingsService,
        idGenerator = idGenerator
    )

    @Test
    fun `formats jsoup source and writes new latest snapshot`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        val newSnapshotId = UUID.randomUUID()
        val source = createActiveSource(sourceId, userId, "raw extracted content", false, "jsoup")
        val latestSnapshot = SharedSourceSnapshot(
            id = UUID.randomUUID(),
            urlNormalized = source.url.normalized,
            sourceType = SourceType.BLOG,
            status = SharedSourceSnapshotStatus.ACTIVE,
            content = Content.from("raw extracted content"),
            metadata = Metadata(
                title = "Snapshot title",
                aiFormatted = false,
                extractionProvider = "jsoup"
            ),
            fetchedAt = Instant.now().minusSeconds(60),
            expiresAt = Instant.now().plusSeconds(3_600),
            version = 1,
            isLatest = true
        )

        whenever(sourceRepository.findByIdAndUserId(sourceId, userId)).thenReturn(source)
        whenever(userAiSettingsService.resolveUseCaseSelection(userId, UserAiSettingsService.SOURCE_FORMATTING))
            .thenReturn(AiModelSelection(provider = "zhipuai", model = "glm-4.7"))
        whenever(jsoupFormatter.supports(ExtractionProviderId.JSOUP)).thenReturn(true)
        whenever(jsoupFormatter.format("raw extracted content", "zhipuai", "glm-4.7")).thenReturn("# formatted markdown")
        whenever(sourceRepository.save(any())).thenAnswer { it.arguments[0] as Source }
        whenever(sharedSourceSnapshotRepository.findFirstByUrlNormalizedAndIsLatestTrue(source.url.normalized))
            .thenReturn(latestSnapshot)
        whenever(sharedSourceSnapshotRepository.findMaxVersionByUrlNormalized(source.url.normalized)).thenReturn(1)
        whenever(idGenerator.newId()).thenReturn(newSnapshotId)

        service.formatSourceContent(sourceId, userId, ExtractionProviderId.JSOUP)

        assertEquals("# formatted markdown", source.content?.text)
        assertTrue(source.metadata?.aiFormatted == true)
        assertEquals("jsoup", source.metadata?.extractionProvider)

        verify(sharedSourceSnapshotRepository).markLatestAsNotLatest(any(), any())
        val snapshotCaptor = argumentCaptor<SharedSourceSnapshot>()
        verify(sharedSourceSnapshotRepository).save(snapshotCaptor.capture())
        assertEquals(newSnapshotId, snapshotCaptor.firstValue.id)
        assertEquals(2, snapshotCaptor.firstValue.version)
        assertTrue(snapshotCaptor.firstValue.metadata?.aiFormatted == true)
        assertEquals("jsoup", snapshotCaptor.firstValue.metadata?.extractionProvider)
    }

    @Test
    fun `skips formatting when extractor has no formatter`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        val source = createActiveSource(sourceId, userId, "raw extracted content", false, "firecrawl")

        whenever(sourceRepository.findByIdAndUserId(sourceId, userId)).thenReturn(source)
        whenever(jsoupFormatter.supports(ExtractionProviderId.FIRECRAWL)).thenReturn(false)
        whenever(youtubeFormatter.supports(ExtractionProviderId.FIRECRAWL)).thenReturn(false)

        service.formatSourceContent(sourceId, userId, ExtractionProviderId.FIRECRAWL)

        verify(sourceRepository, never()).save(any())
        verify(sharedSourceSnapshotRepository, never()).save(any())
    }

    @Test
    fun `skips formatting when source is already ai formatted`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        val source = createActiveSource(sourceId, userId, "already formatted", true, "jsoup")

        whenever(sourceRepository.findByIdAndUserId(sourceId, userId)).thenReturn(source)

        service.formatSourceContent(sourceId, userId, ExtractionProviderId.JSOUP)

        verify(jsoupFormatter, never()).format(any(), any(), any())
        verify(sourceRepository, never()).save(any())
    }

    @Test
    fun `formats youtube captions with forced google flash lite model`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        val snapshotId = UUID.randomUUID()
        val source = createActiveSource(
            sourceId = sourceId,
            userId = userId,
            text = "caption line one caption line two",
            aiFormatted = false,
            extractionProvider = "youtube",
            platform = "youtube",
            videoId = "xgpLjLHB5sA",
            transcriptSource = "captions"
        )
        val latestSnapshot = SharedSourceSnapshot(
            id = UUID.randomUUID(),
            urlNormalized = source.url.normalized,
            sourceType = SourceType.VIDEO,
            status = SharedSourceSnapshotStatus.ACTIVE,
            content = Content.from("caption line one caption line two"),
            metadata = Metadata(
                title = "Video title",
                platform = "youtube",
                aiFormatted = false,
                extractionProvider = "youtube",
                videoId = "xgpLjLHB5sA",
                transcriptSource = "captions"
            ),
            fetchedAt = Instant.now().minusSeconds(60),
            expiresAt = Instant.now().plusSeconds(3_600),
            version = 1,
            isLatest = true
        )

        whenever(jsoupFormatter.supports(ExtractionProviderId.YOUTUBE)).thenReturn(false)
        whenever(youtubeFormatter.supports(ExtractionProviderId.YOUTUBE)).thenReturn(true)
        whenever(youtubeFormatter.format(any(), any(), any())).thenReturn("Caption line one.\n\nCaption line two.")
        whenever(sourceRepository.findByIdAndUserId(sourceId, userId)).thenReturn(source)
        whenever(sourceRepository.save(any())).thenAnswer { it.arguments[0] as Source }
        whenever(sharedSourceSnapshotRepository.findFirstByUrlNormalizedAndIsLatestTrue(source.url.normalized))
            .thenReturn(latestSnapshot)
        whenever(sharedSourceSnapshotRepository.findMaxVersionByUrlNormalized(source.url.normalized)).thenReturn(1)
        whenever(idGenerator.newId()).thenReturn(snapshotId)

        service.formatSourceContent(sourceId, userId, ExtractionProviderId.YOUTUBE)

        verify(userAiSettingsService, never()).resolveUseCaseSelection(any(), any())
        verify(youtubeFormatter).format(
            "caption line one caption line two",
            "google_genai",
            "gemini-2.5-flash-lite"
        )
        assertEquals("xgpLjLHB5sA", source.metadata?.videoId)
        assertEquals("captions", source.metadata?.transcriptSource)
        assertTrue(source.metadata?.aiFormatted == true)
    }

    @Test
    fun `skips youtube formatting for non-caption transcript`() {
        val userId = UUID.randomUUID()
        val sourceId = UUID.randomUUID()
        val source = createActiveSource(
            sourceId = sourceId,
            userId = userId,
            text = "whisper transcript text",
            aiFormatted = false,
            extractionProvider = "youtube",
            platform = "youtube",
            videoId = "xgpLjLHB5sA",
            transcriptSource = "whisper"
        )
        whenever(sourceRepository.findByIdAndUserId(sourceId, userId)).thenReturn(source)

        service.formatSourceContent(sourceId, userId, ExtractionProviderId.YOUTUBE)

        verify(youtubeFormatter, never()).format(any(), any(), any())
        verify(sourceRepository, never()).save(any())
    }

    private fun createActiveSource(
        sourceId: UUID,
        userId: UUID,
        text: String,
        aiFormatted: Boolean,
        extractionProvider: String,
        platform: String = "web",
        videoId: String? = null,
        transcriptSource: String? = null
    ): Source {
        val source = Source.create(
            id = sourceId,
            rawUrl = "https://example.com/article",
            userId = userId,
            sourceType = SourceType.BLOG
        )
        source.startExtraction()
        val content = Content.from(text)
        source.completeExtraction(
            content,
            Metadata.from(
                title = "Example",
                author = "Author",
                publishedDate = Instant.parse("2025-01-01T00:00:00Z"),
                platform = platform,
                wordCount = content.wordCount,
                aiFormatted = aiFormatted,
                extractionProvider = extractionProvider,
                videoId = videoId,
                transcriptSource = transcriptSource
            )
        )
        return source
    }
}
