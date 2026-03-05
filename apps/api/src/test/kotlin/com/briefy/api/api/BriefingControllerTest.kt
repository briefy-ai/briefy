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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
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

    @Autowired
    lateinit var briefingRunRepository: BriefingRunRepository

    @Autowired
    lateinit var subagentRunRepository: SubagentRunRepository

    @Autowired
    lateinit var synthesisRunRepository: SynthesisRunRepository

    @Autowired
    lateinit var runEventRepository: RunEventRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

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

    @Test
    @Transactional
    fun `GET run summary returns active snapshot metrics and reused subagent flag`() {
        val briefingId = createBriefingId("https://example.com/briefing-run-summary-active")
        val now = Instant.now()
        val runId = UUID.randomUUID()
        briefingRunRepository.save(
            BriefingRun(
                id = runId,
                briefingId = briefingId,
                executionFingerprint = "fingerprint-active",
                status = BriefingRunStatus.RUNNING,
                createdAt = now.minusSeconds(45),
                updatedAt = now.minusSeconds(10),
                startedAt = now.minusSeconds(40),
                deadlineAt = now.plusSeconds(120),
                totalPersonas = 2,
                requiredForSynthesis = 1,
                nonEmptySucceededCount = 1
            )
        )

        val reusedSubagentId = UUID.randomUUID()
        subagentRunRepository.saveAll(
            listOf(
                SubagentRun(
                    id = reusedSubagentId,
                    briefingRunId = runId,
                    briefingId = briefingId,
                    personaKey = "macro_analyst",
                    status = SubagentRunStatus.SUCCEEDED,
                    attempt = 1,
                    maxAttempts = 3,
                    startedAt = now.minusSeconds(35),
                    endedAt = now.minusSeconds(20),
                    toolStatsJson = """{"toolCallsTotal": 2}""",
                    reused = true,
                    createdAt = now.minusSeconds(35),
                    updatedAt = now.minusSeconds(20)
                ),
                SubagentRun(
                    id = UUID.randomUUID(),
                    briefingRunId = runId,
                    briefingId = briefingId,
                    personaKey = "risk_analyst",
                    status = SubagentRunStatus.RETRY_WAIT,
                    attempt = 2,
                    maxAttempts = 3,
                    startedAt = now.minusSeconds(15),
                    deadlineAt = now.plusSeconds(80),
                    lastErrorCode = "timeout",
                    lastErrorRetryable = true,
                    lastErrorMessage = "provider timeout",
                    reused = false,
                    createdAt = now.minusSeconds(15),
                    updatedAt = now.minusSeconds(5)
                )
            )
        )

        synthesisRunRepository.save(
            SynthesisRun(
                id = UUID.randomUUID(),
                briefingRunId = runId,
                status = SynthesisRunStatus.RUNNING,
                inputPersonaCount = 1,
                includedPersonaKeysJson = """["macro_analyst"]""",
                excludedPersonaKeysJson = """["risk_analyst"]""",
                startedAt = now.minusSeconds(2),
                createdAt = now.minusSeconds(2),
                updatedAt = now.minusSeconds(1)
            )
        )

        insertRunEventRow(
            briefingRunId = runId,
            subagentRunId = reusedSubagentId,
            eventType = "subagent.tool_call.started",
            occurredAt = now.minusSeconds(34),
            sequenceId = 1L,
            attempt = 1,
            payloadJson = """{"tool":"source_lookup"}""",
            createdAt = now.minusSeconds(34)
        )
        insertRunEventRow(
            briefingRunId = runId,
            subagentRunId = reusedSubagentId,
            eventType = "subagent.tool_call.started",
            occurredAt = now.minusSeconds(30),
            sequenceId = 2L,
            attempt = 1,
            payloadJson = """{"tool":"web_search"}""",
            createdAt = now.minusSeconds(30)
        )

        mockMvc.perform(get("/api/briefings/$briefingId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.executionRunId").value(runId.toString()))

        mockMvc.perform(get("/api/briefings/runs/$runId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.briefingRun.status").value("running"))
            .andExpect(jsonPath("$.subagents.length()").value(2))
            .andExpect(jsonPath("$.subagents[0].reused").value(true))
            .andExpect(jsonPath("$.synthesis.status").value("running"))
            .andExpect(jsonPath("$.metrics.subagentSucceeded").value(1))
            .andExpect(jsonPath("$.metrics.subagentFailed").value(0))
            .andExpect(jsonPath("$.metrics.toolCallsTotal").value(2))
    }

    @Test
    fun `GET run summary returns canonical failure code and terminal timestamps for failed run`() {
        val briefingId = createBriefingId("https://example.com/briefing-run-summary-failed")
        val now = Instant.now()
        val runId = UUID.randomUUID()
        briefingRunRepository.save(
            BriefingRun(
                id = runId,
                briefingId = briefingId,
                executionFingerprint = "fingerprint-failed",
                status = BriefingRunStatus.FAILED,
                createdAt = now.minusSeconds(120),
                updatedAt = now.minusSeconds(1),
                startedAt = now.minusSeconds(110),
                endedAt = now.minusSeconds(2),
                deadlineAt = now.plusSeconds(60),
                totalPersonas = 3,
                requiredForSynthesis = 2,
                nonEmptySucceededCount = 1,
                failureCode = BriefingRunFailureCode.SYNTHESIS_GATE_NOT_MET,
                failureMessage = "Not enough successful personas"
            )
        )

        mockMvc.perform(get("/api/briefings/runs/$runId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.briefingRun.status").value("failed"))
            .andExpect(jsonPath("$.briefingRun.failureCode").value("synthesis_gate_not_met"))
            .andExpect(jsonPath("$.briefingRun.endedAt").isString)
    }

    @Test
    @Transactional
    fun `GET run events supports cursor pagination continuity without duplicates or gaps`() {
        val briefingId = createBriefingId("https://example.com/briefing-run-events")
        val runId = UUID.randomUUID()
        briefingRunRepository.save(
            BriefingRun(
                id = runId,
                briefingId = briefingId,
                executionFingerprint = "fingerprint-events",
                status = BriefingRunStatus.RUNNING,
                totalPersonas = 2,
                requiredForSynthesis = 1
            )
        )
        val subagentRunId = UUID.randomUUID()
        subagentRunRepository.save(
            SubagentRun(
                id = subagentRunId,
                briefingRunId = runId,
                briefingId = briefingId,
                personaKey = "event_persona",
                status = SubagentRunStatus.RUNNING
            )
        )

        val t0 = Instant.parse("2026-03-01T10:00:00Z")
        repeat(5) { idx ->
            insertRunEventRow(
                briefingRunId = runId,
                subagentRunId = subagentRunId,
                eventType = "event-${idx + 1}",
                occurredAt = if (idx < 2) t0 else t0.plusSeconds((idx - 1).toLong()),
                sequenceId = (idx + 1).toLong(),
                attempt = idx + 1,
                payloadJson = """{"order":${idx + 1}}""",
                createdAt = t0.plusSeconds(idx.toLong())
            )
        }

        val firstPageResult = mockMvc.perform(
            get("/api/briefings/runs/$runId/events")
                .param("limit", "2")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].eventId").isString)
            .andExpect(jsonPath("$.items[0].eventType").isString)
            .andExpect(jsonPath("$.items[0].ts").isString)
            .andExpect(jsonPath("$.items[0].briefingRunId").value(runId.toString()))
            .andExpect(jsonPath("$.items[0].payload.order").isNumber)
            .andExpect(jsonPath("$.items[0].attempt").isNumber)
            .andExpect(jsonPath("$.hasMore").value(true))
            .andReturn()
        val firstPage = objectMapper.readTree(firstPageResult.response.contentAsString)
        val firstCursor = firstPage["nextCursor"].asText()
        val collectedEventIds = mutableListOf<UUID>()
        firstPage["items"].forEach { node ->
            collectedEventIds.add(UUID.fromString(node["eventId"].asText()))
        }

        val secondPageResult = mockMvc.perform(
            get("/api/briefings/runs/$runId/events")
                .param("limit", "2")
                .param("cursor", firstCursor)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.hasMore").value(true))
            .andReturn()
        val secondPage = objectMapper.readTree(secondPageResult.response.contentAsString)
        val secondCursor = secondPage["nextCursor"].asText()
        secondPage["items"].forEach { node ->
            collectedEventIds.add(UUID.fromString(node["eventId"].asText()))
        }

        val thirdPageResult = mockMvc.perform(
            get("/api/briefings/runs/$runId/events")
                .param("limit", "2")
                .param("cursor", secondCursor)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.hasMore").value(false))
            .andExpect(jsonPath("$.nextCursor").value(org.hamcrest.Matchers.nullValue()))
            .andReturn()
        val thirdPage = objectMapper.readTree(thirdPageResult.response.contentAsString)
        thirdPage["items"].forEach { node ->
            collectedEventIds.add(UUID.fromString(node["eventId"].asText()))
        }

        val expectedOrder = runEventRepository.findByBriefingRunIdOrderByOccurredAtAscSequenceIdAsc(runId).map { it.eventId }
        assertEquals(expectedOrder, collectedEventIds)
        assertEquals(expectedOrder.size, collectedEventIds.toSet().size)
    }

    @Test
    fun `GET run events returns bad request for invalid cursor`() {
        val briefingId = createBriefingId("https://example.com/briefing-run-events-invalid-cursor")
        val runId = UUID.randomUUID()
        briefingRunRepository.save(
            BriefingRun(
                id = runId,
                briefingId = briefingId,
                executionFingerprint = "fingerprint-invalid-cursor",
                status = BriefingRunStatus.RUNNING,
                totalPersonas = 1,
                requiredForSynthesis = 1
            )
        )

        mockMvc.perform(
            get("/api/briefings/runs/$runId/events")
                .param("cursor", "invalid-cursor")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Invalid cursor"))
    }

    @Test
    fun `GET run summary returns not found when run does not exist`() {
        mockMvc.perform(get("/api/briefings/runs/${UUID.randomUUID()}"))
            .andExpect(status().isNotFound)
    }

    private fun createBriefingId(sourceUrl: String): UUID {
        val source = createActiveSource(sourceUrl)
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
        return UUID.fromString(objectMapper.readTree(createResult.response.contentAsString).get("id").asText())
    }

    private fun insertRunEventRow(
        briefingRunId: UUID,
        subagentRunId: UUID?,
        eventType: String,
        occurredAt: Instant,
        sequenceId: Long,
        attempt: Int?,
        payloadJson: String?,
        createdAt: Instant
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO run_events (
                id, event_id, briefing_run_id, subagent_run_id, event_type,
                occurred_at, sequence_id, attempt, payload_json, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            briefingRunId,
            subagentRunId,
            eventType,
            Timestamp.from(occurredAt),
            sequenceId,
            attempt,
            payloadJson,
            Timestamp.from(createdAt)
        )
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
