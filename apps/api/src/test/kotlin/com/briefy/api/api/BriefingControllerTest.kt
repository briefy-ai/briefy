package com.briefy.api.api

import com.briefy.api.domain.knowledgegraph.briefing.*
import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.domain.knowledgegraph.source.Url
import com.briefy.api.infrastructure.security.CurrentUserProvider
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class BriefingControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var sourceRepository: SourceRepository

    @Autowired
    lateinit var briefingRepository: BriefingRepository

    @Autowired
    lateinit var briefingReferenceRepository: BriefingReferenceRepository

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @MockitoBean
    lateinit var currentUserProvider: CurrentUserProvider

    private val testUserId: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")

    @BeforeEach
    fun setupCurrentUser() {
        `when`(currentUserProvider.requireUserId()).thenReturn(testUserId)
    }

    @Test
    fun `POST creates briefing with plan`() {
        val source = createActiveSource("https://example.com/briefing-create")

        mockMvc.perform(
            post("/api/briefings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceIds": ["${source.id}"],
                      "enrichmentIntent": "deep_dive"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isString)
            .andExpect(jsonPath("$.status").value("plan_pending_approval"))
            .andExpect(jsonPath("$.enrichmentIntent").value("deep_dive"))
            .andExpect(jsonPath("$.sourceIds[0]").value(source.id.toString()))
            .andExpect(jsonPath("$.plan").isArray)
            .andExpect(jsonPath("$.plan.length()").value(3))
    }

    @Test
    fun `GET list and GET by id return briefing state for polling clients`() {
        val source = createActiveSource("https://example.com/briefing-get")
        val createResult = mockMvc.perform(
            post("/api/briefings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceIds": ["${source.id}"],
                      "enrichmentIntent": "contextual_expansion"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andReturn()

        val briefingId = UUID.fromString(objectMapper.readTree(createResult.response.contentAsString).get("id").asText())

        mockMvc.perform(get("/api/briefings"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id=='$briefingId')]").isNotEmpty)

        mockMvc.perform(get("/api/briefings/$briefingId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(briefingId.toString()))
            .andExpect(jsonPath("$.status").value("plan_pending_approval"))
            .andExpect(jsonPath("$.plan").isArray)
            .andExpect(jsonPath("$.sourceIds").isArray)
            .andExpect(jsonPath("$.references").isArray)
            .andExpect(jsonPath("$.citations").isArray)
    }

    @Test
    fun `POST approve transitions briefing to approved`() {
        val source = createActiveSource("https://example.com/briefing-approve")
        val createResult = mockMvc.perform(
            post("/api/briefings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceIds": ["${source.id}"],
                      "enrichmentIntent": "deep_dive"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andReturn()

        val briefingId = UUID.fromString(objectMapper.readTree(createResult.response.contentAsString).get("id").asText())

        mockMvc.perform(post("/api/briefings/$briefingId/approve"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(briefingId.toString()))
            .andExpect(jsonPath("$.status").value("approved"))
            .andExpect(jsonPath("$.approvedAt").isString)
    }

    @Test
    fun `POST retry returns bad request for non-failed briefing`() {
        val source = createActiveSource("https://example.com/briefing-retry-bad")
        val createResult = mockMvc.perform(
            post("/api/briefings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceIds": ["${source.id}"],
                      "enrichmentIntent": "truth_grounding"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andReturn()

        val briefingId = UUID.fromString(objectMapper.readTree(createResult.response.contentAsString).get("id").asText())

        mockMvc.perform(post("/api/briefings/$briefingId/retry"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `POST retry resets failed briefing and GET maps citations references conflicts`() {
        val source = createActiveSource("https://example.com/briefing-retry-ready")
        val createResult = mockMvc.perform(
            post("/api/briefings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceIds": ["${source.id}"],
                      "enrichmentIntent": "truth_grounding"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andReturn()

        val briefingId = UUID.fromString(objectMapper.readTree(createResult.response.contentAsString).get("id").asText())
        val briefing = briefingRepository.findByIdAndUserId(briefingId, testUserId)
        assertNotNull(briefing)
        briefing!!.approve()
        briefing.startGeneration()

        val citationsJson = """
            [
              {"label":"[1]","type":"source","title":"Source","url":"/sources/${source.id}","sourceId":"${source.id}","referenceId":null},
              {"label":"[2]","type":"reference","title":"Ref","url":"https://ref.example.com","sourceId":null,"referenceId":"00000000-0000-0000-0000-000000000099"}
            ]
        """.trimIndent()
        val conflictsJson = """
            [
              {"claim":"A","counterClaim":"B","confidence":0.9,"evidenceCitationLabels":["[1]","[2]"]}
            ]
        """.trimIndent()

        briefing.completeGeneration("# Ready", citationsJson, conflictsJson)
        briefing.status = BriefingStatus.GENERATING
        briefing.failGeneration("{\"code\":\"generation_failed\",\"message\":\"boom\",\"retryable\":true}")
        briefingRepository.save(briefing)

        briefingReferenceRepository.save(
            BriefingReference(
                id = UUID.fromString("00000000-0000-0000-0000-000000000099"),
                briefingId = briefing.id,
                userId = testUserId,
                url = "https://ref.example.com",
                title = "Ref",
                snippet = "snippet",
                status = BriefingReferenceStatus.ACTIVE,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )

        mockMvc.perform(get("/api/briefings/$briefingId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("failed"))
            .andExpect(jsonPath("$.citations.length()").value(2))
            .andExpect(jsonPath("$.references.length()").value(1))
            .andExpect(jsonPath("$.conflictHighlights.length()").value(1))
            .andExpect(jsonPath("$.error.code").value("generation_failed"))

        mockMvc.perform(post("/api/briefings/$briefingId/retry"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("plan_pending_approval"))
            .andExpect(jsonPath("$.citations.length()").value(0))
            .andExpect(jsonPath("$.references.length()").value(0))
            .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.nullValue()))
    }

    private fun createActiveSource(url: String): Source {
        val source = Source.create(
            id = UUID.randomUUID(),
            rawUrl = url,
            userId = testUserId
        )
        source.startExtraction()
        val content = Content.from("This is a source content body used for briefing tests.")
        val metadata = Metadata.from(
            title = "Test Source",
            author = "Author",
            publishedDate = Instant.now(),
            platform = "web",
            wordCount = content.wordCount,
            aiFormatted = true,
            extractionProvider = "jsoup"
        )
        source.completeExtraction(content, metadata)
        return sourceRepository.save(source)
    }
}
