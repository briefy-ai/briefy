package com.briefy.api.api

import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.topic.Topic
import com.briefy.api.domain.knowledgegraph.topic.TopicRepository
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLink
import com.briefy.api.domain.knowledgegraph.topiclink.TopicLinkRepository
import com.briefy.api.infrastructure.extraction.ExtractionProvider
import com.briefy.api.infrastructure.extraction.ExtractionProviderId
import com.briefy.api.infrastructure.extraction.ExtractionProviderResolver
import com.briefy.api.infrastructure.extraction.ExtractionResult
import com.briefy.api.infrastructure.id.IdGenerator
import com.briefy.api.infrastructure.security.CurrentUserProvider
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class TopicControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var topicRepository: TopicRepository

    @Autowired
    lateinit var topicLinkRepository: TopicLinkRepository

    @Autowired
    lateinit var sourceRepository: SourceRepository

    @Autowired
    lateinit var idGenerator: IdGenerator

    @MockitoBean
    lateinit var extractionProviderResolver: ExtractionProviderResolver

    @MockitoBean
    lateinit var currentUserProvider: CurrentUserProvider

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val testUserId: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val extractionProvider: ExtractionProvider = mock()

    @BeforeEach
    fun setupCurrentUser() {
        `when`(currentUserProvider.requireUserId()).thenReturn(testUserId)
        `when`(extractionProviderResolver.resolveProvider(any(), any())).thenReturn(extractionProvider)
        `when`(extractionProvider.id).thenReturn(ExtractionProviderId.JSOUP)
        `when`(extractionProvider.extract(any())).thenReturn(
            ExtractionResult(
                text = "Topic test content with enough words to produce deterministic extraction output.",
                title = "Topic Test",
                author = "Briefy",
                publishedDate = Instant.parse("2025-01-01T00:00:00Z")
            )
        )
    }

    @Test
    fun `GET source topic suggestions returns pending suggestions`() {
        val sourceId = createSource("https://topic-suggestions-test.com/article")
        val suggestionId = createSuggestedTopicForSource(sourceId, "Domain-Driven Design")
        assertNotNull(suggestionId)

        mockMvc.perform(get("/api/sources/$sourceId/topics/suggestions"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$[0].topicLinkId").value(suggestionId.toString()))
            .andExpect(jsonPath("$[0].topicName").value("Domain-Driven Design"))
            .andExpect(jsonPath("$[0].topicStatus").value("suggested"))
    }

    @Test
    fun `POST apply keeps selected suggestions and dismisses the rest`() {
        val sourceId = createSource("https://topic-apply-test.com/article")
        val keepId = createSuggestedTopicForSource(sourceId, "Kotlin")
        createSuggestedTopicForSource(sourceId, "Observability")

        mockMvc.perform(
            post("/api/sources/$sourceId/topics/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"keepTopicLinkIds":["$keepId"]}""")
        )
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/sources/$sourceId/topics/suggestions"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isEmpty)

        mockMvc.perform(get("/api/sources/$sourceId/topics/active"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.topicName=='Kotlin')]").isNotEmpty)
            .andExpect(jsonPath("$[?(@.topicName=='Observability')]").isEmpty)
    }

    @Test
    fun `POST apply rejects keep ids outside pending suggestion set`() {
        val sourceId = createSource("https://topic-apply-invalid-test.com/article")
        createSuggestedTopicForSource(sourceId, "Event Sourcing")
        val unknownSuggestionId = idGenerator.newId()

        mockMvc.perform(
            post("/api/sources/$sourceId/topics/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"keepTopicLinkIds":["$unknownSuggestionId"]}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("keepTopicLinkIds must reference pending suggestions for this source"))
    }

    @Test
    fun `POST topics creates active topic and links selected active sources`() {
        val sourceA = createSource("https://topic-create-links-a.com/article")
        val sourceB = createSource("https://topic-create-links-b.com/article")

        mockMvc.perform(
            post("/api/topics")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Policy Analysis","sourceIds":["$sourceA","$sourceB"]}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Policy Analysis"))
            .andExpect(jsonPath("$.status").value("active"))
            .andExpect(jsonPath("$.origin").value("user"))
            .andExpect(jsonPath("$.linkedSourcesCount").value(2))
    }

    @Test
    fun `POST topics returns conflict for duplicate active topic name`() {
        val topicName = "Economics ${UUID.randomUUID()}"
        mockMvc.perform(
            post("/api/topics")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$topicName"}""")
        )
            .andExpect(status().isCreated)

        val duplicateName = "  ${topicName.lowercase()}  "
        mockMvc.perform(
            post("/api/topics")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"$duplicateName"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Topic already exists: $topicName"))
    }

    @Test
    fun `POST source manual topic creates suggested link`() {
        val sourceId = createSource("https://topic-manual-source-test.com/article")

        mockMvc.perform(
            post("/api/sources/$sourceId/topics/manual")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Public Policy"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.topicName").value("Public Policy"))

        mockMvc.perform(get("/api/sources/$sourceId/topics/suggestions"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.topicName=='Public Policy')]").isNotEmpty)
    }

    @Test
    fun `POST source manual topic returns conflict when topic already active for source`() {
        val sourceId = createSource("https://topic-manual-conflict-test.com/article")
        val suggestionId = createSuggestedTopicForSource(sourceId, "Security")

        mockMvc.perform(
            post("/api/sources/$sourceId/topics/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"keepTopicLinkIds":["$suggestionId"]}""")
        )
            .andExpect(status().isNoContent)

        mockMvc.perform(
            post("/api/sources/$sourceId/topics/manual")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Security"}""")
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `POST apply deletes orphan suggested topics`() {
        val sourceId = createSource("https://topic-orphan-delete-test.com/article")
        createSuggestedTopicForSource(sourceId, "Temporary Suggestion")

        mockMvc.perform(
            post("/api/sources/$sourceId/topics/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"keepTopicLinkIds":[]}""")
        )
            .andExpect(status().isNoContent)

        val topic = topicRepository.findByUserIdAndNameNormalized(
            testUserId,
            Topic.normalizeName("Temporary Suggestion")
        )
        org.junit.jupiter.api.Assertions.assertNull(topic)
    }

    @Test
    fun `topic list and detail exclude archived sources from active topic links`() {
        val sourceId = createSource("https://topic-archive-filter-test.com/article")
        val suggestionId = createSuggestedTopicForSource(sourceId, "Elections")

        mockMvc.perform(
            post("/api/sources/$sourceId/topics/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"keepTopicLinkIds":["$suggestionId"]}""")
        )
            .andExpect(status().isNoContent)

        val topicId = findTopicIdByName("Elections", "active")

        mockMvc.perform(get("/api/topics"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.name=='Elections' && @.linkedSourcesCount==1)]").isNotEmpty)

        mockMvc.perform(delete("/api/sources/$sourceId"))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/topics"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.name=='Elections' && @.linkedSourcesCount==0)]").isNotEmpty)

        mockMvc.perform(get("/api/topics/$topicId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.linkedSources").isEmpty)
    }

    @Test
    fun `GET topic detail returns suggested source links for suggested topic`() {
        val sourceId = createSource("https://topic-detail-suggested-test.com/article")
        createSuggestedTopicForSource(sourceId, "CQRS")
        val topicId = findTopicIdByName("CQRS", "suggested")

        mockMvc.perform(get("/api/topics/$topicId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("suggested"))
            .andExpect(jsonPath("$.linkedSources[0].id").value(sourceId))
    }

    private fun createSource(url: String): String {
        val result = mockMvc.perform(
            post("/api/sources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url":"$url"}""")
        )
            .andExpect(status().isCreated)
            .andReturn()

        return objectMapper.readTree(result.response.contentAsString).get("id").asText()
    }

    private fun createSuggestedTopicForSource(sourceIdRaw: String, topicName: String): UUID {
        val sourceId = UUID.fromString(sourceIdRaw)
        val source = sourceRepository.findByIdAndUserId(sourceId, testUserId)
            ?: error("Source not found for test setup")

        val topic = topicRepository.save(
            Topic.suggestedSystem(
                id = idGenerator.newId(),
                userId = testUserId,
                name = topicName
            )
        )

        val link = topicLinkRepository.save(
            TopicLink.suggestedForSource(
                id = idGenerator.newId(),
                topicId = topic.id,
                sourceId = source.id,
                userId = testUserId,
                confidence = null
            )
        )

        return link.id
    }

    private fun findTopicIdByName(topicName: String, topicStatus: String): String {
        val topicsPayload = mockMvc.perform(get("/api/topics").param("status", topicStatus))
            .andExpect(status().isOk)
            .andReturn()
            .response
            .contentAsString

        val topics = objectMapper.readTree(topicsPayload)
        return topics.firstOrNull { it.get("name").asText() == topicName }?.get("id")?.asText()
            ?: error("Topic not found: $topicName")
    }
}
