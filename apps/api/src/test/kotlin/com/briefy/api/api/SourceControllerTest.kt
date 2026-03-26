package com.briefy.api.api

import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.FormattingState
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.application.topic.TopicSuggestionEventHandler
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.domain.knowledgegraph.source.SourceType
import com.briefy.api.domain.knowledgegraph.source.TopicExtractionState
import com.briefy.api.domain.knowledgegraph.source.Url
import com.briefy.api.domain.knowledgegraph.topic.Topic
import com.briefy.api.domain.knowledgegraph.topic.TopicRepository
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLink
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkRepository
import com.briefy.api.infrastructure.extraction.ExtractionProvider
import com.briefy.api.infrastructure.extraction.ExtractionProviderException
import com.briefy.api.infrastructure.extraction.ExtractionFailureReason
import com.briefy.api.infrastructure.extraction.ExtractionProviderId
import com.briefy.api.infrastructure.extraction.ExtractionProviderResolver
import com.briefy.api.infrastructure.extraction.ExtractionResult
import com.briefy.api.infrastructure.security.CurrentUserProvider
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.`when`
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class SourceControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var sourceRepository: SourceRepository

    @Autowired
    lateinit var topicRepository: TopicRepository

    @Autowired
    lateinit var topicLinkRepository: TopicLinkRepository

    @MockitoBean
    lateinit var extractionProviderResolver: ExtractionProviderResolver

    @MockitoBean
    lateinit var currentUserProvider: CurrentUserProvider

    @MockitoBean
    lateinit var topicSuggestionEventHandler: TopicSuggestionEventHandler

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val testUserId: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val extractionProvider: ExtractionProvider = mock()

    private val sampleExtractionResult = ExtractionResult(
        text = "This is the extracted article content with enough words to test",
        title = "Test Article Title",
        author = "Test Author",
        publishedDate = Instant.parse("2024-01-15T10:00:00Z")
    )

    @BeforeEach
    fun setupCurrentUser() {
        `when`(currentUserProvider.requireUserId()).thenReturn(testUserId)
        `when`(extractionProviderResolver.resolveProvider(any(), any())).thenReturn(extractionProvider)
        `when`(extractionProvider.id).thenReturn(ExtractionProviderId.JSOUP)
    }

    @Test
    fun `POST creates source and extracts content`() {
        `when`(extractionProvider.extract(any())).thenReturn(sampleExtractionResult)

        mockMvc.perform(
            post("/api/sources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url": "https://example.com/article"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.url.normalized").value("https://example.com/article"))
            .andExpect(jsonPath("$.url.platform").value("web"))
            .andExpect(jsonPath("$.status").value("active"))
            .andExpect(jsonPath("$.sourceType").value("blog"))
            .andExpect(jsonPath("$.content.text").value(sampleExtractionResult.text))
            .andExpect(jsonPath("$.content.wordCount").isNumber)
            .andExpect(jsonPath("$.metadata.title").value("Test Article Title"))
            .andExpect(jsonPath("$.metadata.author").value("Test Author"))
            .andExpect(jsonPath("$.metadata.estimatedReadingTime").isNumber)
            .andExpect(jsonPath("$.metadata.formattingState").value("pending"))
            .andExpect(jsonPath("$.metadata.formattingFailureReason").isEmpty)
            .andExpect(jsonPath("$.reuse.usedCache").value(false))
            .andExpect(jsonPath("$.id").isString)
            .andExpect(jsonPath("$.createdAt").isString)
    }

    @Test
    fun `POST reuses fresh cached snapshot across users for same URL`() {
        val userA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        val userB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
        `when`(currentUserProvider.requireUserId()).thenReturn(userA, userB)
        `when`(extractionProvider.extract(any())).thenReturn(sampleExtractionResult)

        mockMvc.perform(
            post("/api/sources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url": "https://shared-cache-test.com/article"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.reuse.usedCache").value(false))

        mockMvc.perform(
            post("/api/sources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url": "https://shared-cache-test.com/article"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.reuse.usedCache").value(true))

        verify(extractionProvider, times(1)).extract(any())
    }

    @Test
    fun `POST returns conflict for duplicate URL`() {
        `when`(extractionProvider.extract(any())).thenReturn(sampleExtractionResult)

        // Create first
        mockMvc.perform(
            post("/api/sources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url": "https://duplicate-test.com/article"}""")
        )
            .andExpect(status().isCreated)

        // Try duplicate
        mockMvc.perform(
            post("/api/sources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url": "https://duplicate-test.com/article"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `POST returns bad request for blank url`() {
        mockMvc.perform(
            post("/api/sources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url": ""}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `POST returns 422 when extraction fails`() {
        `when`(extractionProvider.extract(any())).thenThrow(
            ExtractionProviderException(
                providerId = ExtractionProviderId.FIRECRAWL,
                reason = ExtractionFailureReason.UNSUPPORTED,
                message = "Firecrawl API key is required to extract PostHog URLs. Enable Firecrawl in Settings and provide an API key."
            )
        )

        mockMvc.perform(
            post("/api/sources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url": "https://posthog.com/blog/forward-deployed-engineer"}""")
        )
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.message").value("Firecrawl API key is required to extract PostHog URLs. Enable Firecrawl in Settings and provide an API key."))
    }

    @Test
    fun `GET source includes extraction failure message and retryability`() {
        val failedSource = Source.create(
            id = UUID.randomUUID(),
            rawUrl = "https://youtube.com/watch?v=dQw4w9WgXcQ",
            userId = testUserId,
            sourceType = SourceType.VIDEO
        )
        failedSource.startExtraction()
        failedSource.failExtraction("supadata_invalid_api_key")
        sourceRepository.save(failedSource)

        mockMvc.perform(get("/api/sources/${failedSource.id}"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("failed"))
            .andExpect(jsonPath("$.extractionFailureReason").value("supadata_invalid_api_key"))
            .andExpect(jsonPath("$.extractionFailureMessage").value("Your Supadata API key is invalid. Update it in Settings and try again."))
            .andExpect(jsonPath("$.extractionFailureRetryable").value(false))
    }

    @Test
    fun `GET list returns all sources`() {
        `when`(extractionProvider.extract(any())).thenReturn(sampleExtractionResult)

        // Create a source
        createSource("https://list-test.com/article")

        // List all
        mockMvc.perform(get("/api/sources"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items").isArray)
            .andExpect(jsonPath("$.limit").value(20))
            .andExpect(jsonPath("$.hasMore").isBoolean)
    }

    @Test
    fun `GET list includes pending suggested topics count defaulted to zero`() {
        `when`(extractionProvider.extract(any())).thenReturn(sampleExtractionResult)
        val id = createSource("https://list-count-default-test.com/article")

        mockMvc.perform(get("/api/sources"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[?(@.id=='$id' && @.pendingSuggestedTopicsCount==0)]").isNotEmpty)
    }

    @Test
    fun `GET list filters by status`() {
        mockMvc.perform(get("/api/sources").param("status", "active"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items").isArray)
    }

    @Test
    fun `GET list filters by topic and source type and includes topics`() {
        val matchingSource = saveSource(
            url = "https://filters-test.com/blog",
            title = "Matching Source",
            sourceType = SourceType.BLOG,
            readingTime = 8,
            createdAt = Instant.parse("2025-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2025-01-03T00:00:00Z")
        )
        val excludedByType = saveSource(
            url = "https://filters-test.com/video",
            title = "Video Source",
            sourceType = SourceType.VIDEO,
            readingTime = 4,
            createdAt = Instant.parse("2025-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2025-01-02T00:00:00Z")
        )
        val otherTopicSource = saveSource(
            url = "https://filters-test.com/other-topic",
            title = "Other Topic Source",
            sourceType = SourceType.BLOG,
            readingTime = 6,
            createdAt = Instant.parse("2025-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2025-01-01T12:00:00Z")
        )

        val kotlinTopicId = createActiveTopicLink(matchingSource.id, "Kotlin")
        linkTopicToSource(kotlinTopicId, excludedByType.id)
        createActiveTopicLink(otherTopicSource.id, "Architecture")

        mockMvc.perform(
            get("/api/sources")
                .param("topicIds", kotlinTopicId.toString())
                .param("sourceType", "blog")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value(matchingSource.id.toString()))
            .andExpect(jsonPath("$.items[0].topics[0].name").value("Kotlin"))
    }

    @Test
    fun `GET list sorts by longest read with nulls last`() {
        val longest = saveSource(
            url = "https://sort-test.com/longest",
            title = "Longest",
            readingTime = 15,
            createdAt = Instant.parse("2025-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2025-01-01T03:00:00Z")
        )
        val medium = saveSource(
            url = "https://sort-test.com/medium",
            title = "Medium",
            readingTime = 5,
            createdAt = Instant.parse("2025-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2025-01-01T02:00:00Z")
        )
        val noReadingTime = saveSource(
            url = "https://sort-test.com/null",
            title = "No Reading Time",
            readingTime = null,
            createdAt = Instant.parse("2025-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2025-01-01T01:00:00Z")
        )
        val topic = createActiveTopic("sort-longest")
        linkTopicToSource(topic.id, longest.id)
        linkTopicToSource(topic.id, medium.id)
        linkTopicToSource(topic.id, noReadingTime.id)

        mockMvc.perform(
            get("/api/sources")
                .param("sort", "longest")
                .param("topicIds", topic.id.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].id").value(longest.id.toString()))
            .andExpect(jsonPath("$.items[1].id").value(medium.id.toString()))
            .andExpect(jsonPath("$.items[2].id").value(noReadingTime.id.toString()))
    }

    @Test
    fun `GET list newest sorts by createdAt desc even when updatedAt differs`() {
        val oldestButRecentlyUpdated = saveSource(
            url = "https://sort-test.com/old-created-new-updated",
            title = "Old Created",
            createdAt = Instant.parse("2025-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2025-01-03T00:00:00Z")
        )
        val newestButOlderUpdate = saveSource(
            url = "https://sort-test.com/new-created-old-updated",
            title = "New Created",
            createdAt = Instant.parse("2025-01-02T00:00:00Z"),
            updatedAt = Instant.parse("2025-01-01T12:00:00Z")
        )
        val topic = createActiveTopic("sort-created-desc")
        linkTopicToSource(topic.id, oldestButRecentlyUpdated.id)
        linkTopicToSource(topic.id, newestButOlderUpdate.id)

        mockMvc.perform(
            get("/api/sources")
                .param("sort", "newest")
                .param("topicIds", topic.id.toString())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].id").value(newestButOlderUpdate.id.toString()))
            .andExpect(jsonPath("$.items[1].id").value(oldestButRecentlyUpdated.id.toString()))
    }

    @Test
    fun `GET list returns bad request for invalid status`() {
        mockMvc.perform(get("/api/sources").param("status", "unknown"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `GET list returns bad request for invalid sort`() {
        mockMvc.perform(get("/api/sources").param("sort", "sideways"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Invalid sort"))
    }

    @Test
    fun `GET list supports cursor pagination without duplicates`() {
        `when`(extractionProvider.extract(any())).thenReturn(sampleExtractionResult)

        createSource("https://cursor-page-1.com/article")
        Thread.sleep(5)
        createSource("https://cursor-page-2.com/article")
        Thread.sleep(5)
        createSource("https://cursor-page-3.com/article")

        val firstPageJson = mockMvc.perform(get("/api/sources").param("status", "active").param("limit", "2"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andReturn()
            .response
            .contentAsString

        val firstPage = objectMapper.readTree(firstPageJson)
        val firstPageIds = firstPage.get("items").map { it.get("id").asText() }
        val nextCursor = firstPage.get("nextCursor").asText()
        assertTrue(nextCursor.isNotBlank())

        val secondPageJson = mockMvc.perform(
            get("/api/sources")
                .param("status", "active")
                .param("limit", "2")
                .param("cursor", nextCursor)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andReturn()
            .response
            .contentAsString

        val secondPage = objectMapper.readTree(secondPageJson)
        val secondPageIds = secondPage.get("items").map { it.get("id").asText() }
        assertTrue(secondPageIds.none { firstPageIds.contains(it) })
        assertTrue(secondPage.has("hasMore"))
    }

    @Test
    fun `GET list returns bad request for invalid cursor`() {
        mockMvc.perform(get("/api/sources").param("cursor", "invalid-cursor"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Invalid cursor"))
    }

    @Test
    fun `GET list rejects pre deploy newest cursor prefix`() {
        val oldCursor = Base64.getUrlEncoder().withoutPadding().encodeToString(
            "u|2025-01-02T00:00:00Z|${UUID.randomUUID()}".toByteArray(StandardCharsets.UTF_8)
        )

        mockMvc.perform(get("/api/sources").param("sort", "newest").param("cursor", oldCursor))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Invalid cursor"))
    }

    @Test
    fun `GET list paginates newest by createdAt desc`() {
        val oldest = saveSource(
            url = "https://created-cursor-test.com/oldest",
            title = "Oldest",
            createdAt = Instant.parse("2025-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2025-01-03T00:00:00Z")
        )
        val middle = saveSource(
            url = "https://created-cursor-test.com/middle",
            title = "Middle",
            createdAt = Instant.parse("2025-01-02T00:00:00Z"),
            updatedAt = Instant.parse("2025-01-02T00:00:00Z")
        )
        val newest = saveSource(
            url = "https://created-cursor-test.com/newest",
            title = "Newest",
            createdAt = Instant.parse("2025-01-03T00:00:00Z"),
            updatedAt = Instant.parse("2025-01-01T00:00:00Z")
        )
        val topic = createActiveTopic("sort-created-cursor")
        linkTopicToSource(topic.id, oldest.id)
        linkTopicToSource(topic.id, middle.id)
        linkTopicToSource(topic.id, newest.id)

        val firstPageJson = mockMvc.perform(
            get("/api/sources")
                .param("sort", "newest")
                .param("topicIds", topic.id.toString())
                .param("limit", "2")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].id").value(newest.id.toString()))
            .andExpect(jsonPath("$.items[1].id").value(middle.id.toString()))
            .andReturn()
            .response
            .contentAsString

        val nextCursor = objectMapper.readTree(firstPageJson).get("nextCursor").asText()

        mockMvc.perform(
            get("/api/sources")
                .param("sort", "newest")
                .param("topicIds", topic.id.toString())
                .param("cursor", nextCursor)
                .param("limit", "2")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[?(@.id=='${oldest.id}')]").isNotEmpty)
    }

    @Test
    fun `GET search returns active source matches with topic chips`() {
        val topicName = "AI-${UUID.randomUUID()}"
        val topicMatched = saveSource(
            url = "https://search-test.com/topic-match",
            title = "Unrelated Title",
            author = "Briefy",
            readingTime = 7,
            createdAt = Instant.parse("2025-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2025-01-01T02:00:00Z")
        )
        createActiveTopicLink(topicMatched.id, topicName)
        saveSource(
            url = "https://search-test.com/archived",
            title = "Archived $topicName",
            status = SourceStatus.ARCHIVED,
            createdAt = Instant.parse("2025-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2025-01-01T01:00:00Z")
        )

        mockMvc.perform(get("/api/sources/search").param("q", topicName))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value(topicMatched.id.toString()))
            .andExpect(jsonPath("$.items[0].topics[0].name").value(topicName))
    }

    @Test
    fun `GET by id returns source`() {
        `when`(extractionProvider.extract(any())).thenReturn(sampleExtractionResult)

        // Create a source
        val result = mockMvc.perform(
            post("/api/sources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url": "https://get-test.com/article"}""")
        )
            .andExpect(status().isCreated)
            .andReturn()

        val id = objectMapper.readTree(result.response.contentAsString).get("id").asText()

        // Get by id
        mockMvc.perform(get("/api/sources/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.status").value("active"))
            .andExpect(jsonPath("$.content.text").exists())
    }

    @Test
    fun `GET by id indicates when generated cover image already exists`() {
        `when`(extractionProvider.extract(any())).thenReturn(sampleExtractionResult)

        val id = createSource("https://generated-cover-source.com/article")
        val source = sourceRepository.findById(UUID.fromString(id)).orElseThrow()
        source.featuredImageKey = "images/covers/${source.id}/featured.png"
        sourceRepository.save(source)

        mockMvc.perform(get("/api/sources/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hasGeneratedCoverImage").value(true))
    }

    @Test
    fun `GET by id returns 404 for unknown id`() {
        mockMvc.perform(get("/api/sources/00000000-0000-0000-0000-000000000000"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST retry returns 404 for unknown id`() {
        mockMvc.perform(post("/api/sources/00000000-0000-0000-0000-000000000000/retry"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST retry returns bad request for non-failed source`() {
        `when`(extractionProvider.extract(any())).thenReturn(sampleExtractionResult)

        // Create a source (will be ACTIVE)
        val id = createSource("https://retry-test.com/article")

        // Try to retry an active source
        mockMvc.perform(post("/api/sources/$id/retry"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `POST formatting retry returns 404 for unknown id`() {
        mockMvc.perform(post("/api/sources/00000000-0000-0000-0000-000000000000/formatting/retry"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST formatting retry returns bad request when formatting is not failed`() {
        `when`(extractionProvider.extract(any())).thenReturn(sampleExtractionResult)

        val id = createSource("https://retry-formatting-test.com/article")

        mockMvc.perform(post("/api/sources/$id/formatting/retry"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `POST topic extraction retry returns 404 for unknown id`() {
        mockMvc.perform(post("/api/sources/00000000-0000-0000-0000-000000000000/topics/retry"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST topic extraction retry returns bad request when topic extraction already succeeded`() {
        `when`(extractionProvider.extract(any())).thenReturn(sampleExtractionResult)
        val id = createSource("https://retry-topic-extraction-test.com/article")
        val sourceId = UUID.fromString(id)
        val source = sourceRepository.findByIdAndUserId(sourceId, testUserId)
            ?: error("source not found")
        source.markTopicExtractionSucceeded()
        sourceRepository.save(source)

        mockMvc.perform(post("/api/sources/$id/topics/retry"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `POST topic extraction retry resets state to pending`() {
        `when`(extractionProvider.extract(any())).thenReturn(sampleExtractionResult)
        val id = createSource("https://retry-topic-extraction-failed-test.com/article")
        val sourceId = UUID.fromString(id)
        val source = sourceRepository.findByIdAndUserId(sourceId, testUserId)
            ?: error("source not found")
        source.markTopicExtractionFailed("generation_failed")
        sourceRepository.save(source)

        mockMvc.perform(post("/api/sources/$id/topics/retry"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.topicExtractionState").value("pending"))
            .andExpect(jsonPath("$.topicExtractionFailureReason").isEmpty)
    }

    @Test
    fun `DELETE hard deletes unreferenced source and hides it from list`() {
        `when`(extractionProvider.extract(any())).thenReturn(sampleExtractionResult)
        val id = createSource("https://delete-test.com/article")

        mockMvc.perform(delete("/api/sources/$id"))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/sources"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items").isArray)
            .andExpect(jsonPath("$.items[?(@.id=='$id')]").isEmpty)

        mockMvc.perform(get("/api/sources").param("status", "archived"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[?(@.id=='$id')]").isEmpty)

        mockMvc.perform(get("/api/sources/$id"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE is idempotent for already deleted source`() {
        `when`(extractionProvider.extract(any())).thenReturn(sampleExtractionResult)
        val id = createSource("https://delete-idempotent-test.com/article")

        mockMvc.perform(delete("/api/sources/$id"))
            .andExpect(status().isNoContent)

        mockMvc.perform(delete("/api/sources/$id"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `DELETE returns 204 for unknown id`() {
        mockMvc.perform(delete("/api/sources/00000000-0000-0000-0000-000000000000"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `POST restore returns 404 after source is hard deleted`() {
        `when`(extractionProvider.extract(any())).thenReturn(sampleExtractionResult)
        val id = createSource("https://restore-test.com/article")

        mockMvc.perform(delete("/api/sources/$id"))
            .andExpect(status().isNoContent)

        mockMvc.perform(post("/api/sources/$id/restore"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST restore is idempotent for active source`() {
        `when`(extractionProvider.extract(any())).thenReturn(sampleExtractionResult)
        val id = createSource("https://restore-idempotent-active.com/article")

        mockMvc.perform(post("/api/sources/$id/restore"))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/sources/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("active"))
    }

    @Test
    fun `POST restore returns 404 for unknown id`() {
        mockMvc.perform(post("/api/sources/00000000-0000-0000-0000-000000000000/restore"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST restore returns 404 for source not owned by user`() {
        val userA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        val userB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
        `when`(currentUserProvider.requireUserId()).thenReturn(userB, userB, userA)
        `when`(extractionProvider.extract(any())).thenReturn(sampleExtractionResult)

        val sourceId = createSource("https://restore-ownership-test.com/article")
        mockMvc.perform(delete("/api/sources/$sourceId"))
            .andExpect(status().isNoContent)

        mockMvc.perform(post("/api/sources/$sourceId/restore"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST archive-batch archives all owned sources atomically`() {
        `when`(extractionProvider.extract(any())).thenReturn(sampleExtractionResult)
        val idA = createSource("https://batch-archive-a.com/article")
        val idB = createSource("https://batch-archive-b.com/article")

        mockMvc.perform(
            post("/api/sources/archive-batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sourceIds":["$idA","$idB"]}""")
        )
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/sources/$idA"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("archived"))
        mockMvc.perform(get("/api/sources/$idB"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("archived"))
    }

    @Test
    fun `POST archive-batch returns 404 and archives none when one id is unknown`() {
        `when`(extractionProvider.extract(any())).thenReturn(sampleExtractionResult)
        val idA = createSource("https://batch-archive-unknown-a.com/article")
        val unknownId = UUID.fromString("00000000-0000-0000-0000-000000000000")

        mockMvc.perform(
            post("/api/sources/archive-batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sourceIds":["$idA","$unknownId"]}""")
        )
            .andExpect(status().isNotFound)

        mockMvc.perform(get("/api/sources/$idA"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("active"))
    }

    @Test
    fun `POST archive-batch returns 404 when any source is not owned by user`() {
        val userA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
        val userB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
        `when`(currentUserProvider.requireUserId()).thenReturn(userA, userB, userA)
        `when`(extractionProvider.extract(any())).thenReturn(sampleExtractionResult)

        val ownedByA = createSource("https://batch-owned-a.com/article")
        val ownedByB = createSource("https://batch-owned-b.com/article")

        mockMvc.perform(
            post("/api/sources/archive-batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sourceIds":["$ownedByA","$ownedByB"]}""")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST archive-batch dedupes ids and succeeds`() {
        `when`(extractionProvider.extract(any())).thenReturn(sampleExtractionResult)
        val idA = createSource("https://batch-dedupe.com/article")

        mockMvc.perform(
            post("/api/sources/archive-batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sourceIds":["$idA","$idA"]}""")
        )
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/sources/$idA"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("archived"))

        mockMvc.perform(get("/api/sources").param("status", "archived"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[?(@.id=='$idA' && @.pendingSuggestedTopicsCount==0)]").isNotEmpty)
    }

    @Test
    fun `POST archive-batch returns bad request for empty sourceIds`() {
        mockMvc.perform(
            post("/api/sources/archive-batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sourceIds":[]}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `POST archive-batch returns bad request when sourceIds exceed limit`() {
        val ids = (1..101)
            .map { UUID.randomUUID().toString() }
            .joinToString(separator = "\",\"", prefix = "\"", postfix = "\"")

        mockMvc.perform(
            post("/api/sources/archive-batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sourceIds":[$ids]}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `POST content updates active source content`() {
        `when`(extractionProvider.extract(any())).thenReturn(sampleExtractionResult)
        val id = createSource("https://manual-content-active-test.com/article")

        mockMvc.perform(
            post("/api/sources/$id/content")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"rawText": "Manually pasted article content with enough words"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("active"))
            .andExpect(jsonPath("$.content.text").value("Manually pasted article content with enough words"))
            .andExpect(jsonPath("$.metadata.extractionProvider").value("manual"))
            .andExpect(jsonPath("$.metadata.formattingState").value("pending"))
    }

    @Test
    fun `POST content activates failed source with provided content`() {
        val failedSource = com.briefy.api.domain.knowledgegraph.source.Source.create(
            id = UUID.randomUUID(),
            rawUrl = "https://manual-content-failed-test.com/article",
            userId = testUserId
        )
        failedSource.startExtraction()
        failedSource.failExtraction()
        sourceRepository.save(failedSource)

        mockMvc.perform(
            post("/api/sources/${failedSource.id}/content")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"rawText": "Manually pasted content for failed source", "title": "Custom Title"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("active"))
            .andExpect(jsonPath("$.content.text").value("Manually pasted content for failed source"))
            .andExpect(jsonPath("$.metadata.title").value("Custom Title"))
            .andExpect(jsonPath("$.metadata.extractionProvider").value("manual"))
    }

    @Test
    fun `POST content returns 404 for unknown source id`() {
        mockMvc.perform(
            post("/api/sources/00000000-0000-0000-0000-000000000000/content")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"rawText": "some content"}""")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST content returns 400 for blank rawText`() {
        mockMvc.perform(
            post("/api/sources/00000000-0000-0000-0000-000000000000/content")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"rawText": ""}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `POST content returns 400 for invalid source state`() {
        `when`(extractionProvider.extract(any())).thenReturn(sampleExtractionResult)
        val id = createSource("https://manual-content-archived-test.com/article")

        // Archive the source
        mockMvc.perform(delete("/api/sources/$id"))
            .andExpect(status().isNoContent)

        // Verify it's gone (hard deleted since no dependencies)
        // Create a new source and archive it via batch to get ARCHIVED state
        val id2 = createSource("https://manual-content-archived-test2.com/article")
        mockMvc.perform(
            post("/api/sources/archive-batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"sourceIds":["$id2"]}""")
        )
            .andExpect(status().isNoContent)

        mockMvc.perform(
            post("/api/sources/$id2/content")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"rawText": "some content"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `POST mark-read toggles read field`() {
        `when`(extractionProvider.extract(any())).thenReturn(sampleExtractionResult)
        val id = createSource("https://mark-read-test.com/article")

        mockMvc.perform(post("/api/sources/$id/mark-read"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.read").value(true))
    }

    private fun createSource(url: String): String {
        val result = mockMvc.perform(
            post("/api/sources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url": "$url"}""")
        )
            .andExpect(status().isCreated)
            .andReturn()

        return objectMapper.readTree(result.response.contentAsString).get("id").asText()
    }

    private fun saveSource(
        url: String,
        title: String,
        author: String? = "Test Author",
        sourceType: SourceType = SourceType.BLOG,
        readingTime: Int? = 5,
        status: SourceStatus = SourceStatus.ACTIVE,
        createdAt: Instant,
        updatedAt: Instant
    ): Source {
        val normalizedUrl = Url.from(url)
        val source = Source(
            id = UUID.randomUUID(),
            url = normalizedUrl,
            status = status,
            content = Content.from("Deterministic source content for controller tests."),
            metadata = Metadata(
                title = title,
                author = author,
                platform = normalizedUrl.platform,
                estimatedReadingTime = readingTime,
                aiFormatted = true,
                formattingState = FormattingState.SUCCEEDED
            ),
            sourceType = sourceType,
            userId = testUserId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            isRead = false,
            topicExtractionState = TopicExtractionState.SUCCEEDED
        )
        return sourceRepository.save(source)
    }

    private fun createActiveTopicLink(sourceId: UUID, topicName: String): UUID {
        val topic = createActiveTopic(topicName)
        linkTopicToSource(topic.id, sourceId)
        return topic.id
    }

    private fun createActiveTopic(topicName: String): Topic {
        return topicRepository.save(Topic.activeUser(UUID.randomUUID(), testUserId, topicName))
    }

    private fun linkTopicToSource(topicId: UUID, sourceId: UUID) {
        topicLinkRepository.save(TopicLink.activeUserForSource(UUID.randomUUID(), topicId, sourceId, testUserId))
    }
}
