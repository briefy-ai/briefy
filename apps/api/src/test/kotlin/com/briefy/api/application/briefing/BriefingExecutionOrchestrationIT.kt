package com.briefy.api.application.briefing

import com.briefy.api.domain.knowledgegraph.briefing.*
import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.domain.knowledgegraph.source.Url
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Testcontainers
@TestPropertySource(
    properties = [
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "briefing.execution.enabled=true",
        "briefing.generation.enabled=false",
        "ai.observability.enabled=false"
    ]
)
class BriefingExecutionOrchestrationIT {

    @Autowired
    lateinit var briefingGenerationService: BriefingGenerationService

    @Autowired
    lateinit var briefingRepository: BriefingRepository

    @Autowired
    lateinit var briefingSourceRepository: BriefingSourceRepository

    @Autowired
    lateinit var briefingPlanStepRepository: BriefingPlanStepRepository

    @Autowired
    lateinit var sourceRepository: SourceRepository

    @Autowired
    lateinit var briefingRunRepository: BriefingRunRepository

    @Autowired
    lateinit var subagentRunRepository: SubagentRunRepository

    @Autowired
    lateinit var synthesisRunRepository: SynthesisRunRepository

    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate

    @MockitoBean
    lateinit var synthesisExecutionRunner: SynthesisExecutionRunner

    @BeforeEach
    fun cleanup() {
        jdbcTemplate.update("DELETE FROM run_events")
        jdbcTemplate.update("DELETE FROM synthesis_runs")
        jdbcTemplate.update("DELETE FROM subagent_runs")
        jdbcTemplate.update("DELETE FROM briefing_runs")
        jdbcTemplate.update("DELETE FROM briefing_generation_jobs")
        jdbcTemplate.update("DELETE FROM briefing_plan_steps")
        jdbcTemplate.update("DELETE FROM briefing_references")
        jdbcTemplate.update("DELETE FROM briefing_sources")
        jdbcTemplate.update("DELETE FROM briefings")
        jdbcTemplate.update("DELETE FROM source_embeddings")
        jdbcTemplate.update("DELETE FROM sources")
        jdbcTemplate.update("DELETE FROM users")

        whenever(synthesisExecutionRunner.run(any())).thenReturn(
            BriefingGenerationResult(
                markdownBody = "## Synthesized briefing",
                references = emptyList(),
                conflictHighlights = emptyList()
            )
        )
    }

    @Test
    fun `A1 and C1 - happy path succeeds with execution runtime as source of truth`() {
        val userId = insertUser("execution-orchestration-a1@example.com")
        val sources = listOf(
            createSource(userId, "source-1", "Evidence one"),
            createSource(userId, "source-2", "Evidence two")
        )
        val briefing = createApprovedBriefing(userId)
        linkSources(briefing.id, userId, sources)
        createPlanSteps(
            briefing.id,
            listOf(
                "Analyze source agreement",
                "Extract implications",
                "Write synthesis perspective"
            )
        )

        briefingGenerationService.generateApprovedBriefing(briefing.id, userId)

        val refreshedBriefing = briefingRepository.findById(briefing.id).orElseThrow()
        assertEquals(BriefingStatus.READY, refreshedBriefing.status)
        assertNotNull(refreshedBriefing.contentMarkdown)

        val run = briefingRunRepository.findTopByBriefingIdOrderByCreatedAtDesc(briefing.id)
            ?: error("Expected briefing run")
        assertEquals(BriefingRunStatus.SUCCEEDED, run.status)
        assertEquals(3, run.nonEmptySucceededCount)
        assertEquals(2, run.requiredForSynthesis)

        val subagents = subagentRunRepository.findByBriefingRunIdOrderByCreatedAtAsc(run.id)
        assertTrue(subagents.all { it.status == SubagentRunStatus.SUCCEEDED })

        val synthesis = synthesisRunRepository.findByBriefingRunId(run.id) ?: error("Expected synthesis run")
        assertEquals(SynthesisRunStatus.SUCCEEDED, synthesis.status)

        verify(synthesisExecutionRunner).run(any())
    }

    @Test
    fun `A2 - gate failure skips synthesis and fails briefing`() {
        val userId = insertUser("execution-orchestration-a2@example.com")
        val sources = listOf(createSource(userId, "source-1", "Evidence one"))
        val briefing = createApprovedBriefing(userId)
        linkSources(briefing.id, userId, sources)
        createPlanSteps(
            briefing.id,
            listOf(
                "Normal success task",
                "Another success task",
                "[fail] force fail",
                "[empty] no output",
                "[fail] second fail"
            )
        )

        assertThrows<BriefingGenerationFailedException> {
            briefingGenerationService.generateApprovedBriefing(briefing.id, userId)
        }

        val refreshedBriefing = briefingRepository.findById(briefing.id).orElseThrow()
        assertEquals(BriefingStatus.FAILED, refreshedBriefing.status)
        assertTrue(refreshedBriefing.errorJson?.contains("synthesis_gate_not_met") == true)

        val run = briefingRunRepository.findTopByBriefingIdOrderByCreatedAtDesc(briefing.id)
            ?: error("Expected briefing run")
        assertEquals(BriefingRunStatus.FAILED, run.status)
        assertEquals(BriefingRunFailureCode.SYNTHESIS_GATE_NOT_MET, run.failureCode)

        val synthesis = synthesisRunRepository.findByBriefingRunId(run.id) ?: error("Expected synthesis run")
        assertEquals(SynthesisRunStatus.SKIPPED, synthesis.status)

        verify(synthesisExecutionRunner, never()).run(any())
    }

    @Test
    fun `C2 - synthesis failure marks briefing run failed with synthesis_failed`() {
        val userId = insertUser("execution-orchestration-c2@example.com")
        val sources = listOf(createSource(userId, "source-1", "Evidence one"))
        val briefing = createApprovedBriefing(userId)
        linkSources(briefing.id, userId, sources)
        createPlanSteps(
            briefing.id,
            listOf(
                "Normal success task",
                "Another success task",
                "Third success task"
            )
        )

        whenever(synthesisExecutionRunner.run(any())).thenThrow(RuntimeException("synthesis_down"))

        assertThrows<BriefingGenerationFailedException> {
            briefingGenerationService.generateApprovedBriefing(briefing.id, userId)
        }

        val refreshedBriefing = briefingRepository.findById(briefing.id).orElseThrow()
        assertEquals(BriefingStatus.FAILED, refreshedBriefing.status)
        assertTrue(refreshedBriefing.errorJson?.contains("synthesis_failed") == true)

        val run = briefingRunRepository.findTopByBriefingIdOrderByCreatedAtDesc(briefing.id)
            ?: error("Expected briefing run")
        assertEquals(BriefingRunStatus.FAILED, run.status)
        assertEquals(BriefingRunFailureCode.SYNTHESIS_FAILED, run.failureCode)

        val synthesis = synthesisRunRepository.findByBriefingRunId(run.id) ?: error("Expected synthesis run")
        assertEquals(SynthesisRunStatus.FAILED, synthesis.status)
    }

    @Test
    fun `E1 - existing active run is reused without creating duplicate run rows`() {
        val userId = insertUser("execution-orchestration-e1@example.com")
        val sources = listOf(createSource(userId, "source-1", "Evidence one"))
        val briefing = createApprovedBriefing(userId)
        linkSources(briefing.id, userId, sources)
        val planSteps = createPlanSteps(
            briefing.id,
            listOf("Task one", "Task two", "Task three")
        )

        val now = Instant.now()
        val run = briefingRunRepository.save(
            BriefingRun(
                id = UUID.randomUUID(),
                briefingId = briefing.id,
                executionFingerprint = "prefilled-fingerprint",
                status = BriefingRunStatus.QUEUED,
                createdAt = now,
                updatedAt = now,
                totalPersonas = planSteps.size,
                requiredForSynthesis = 2,
                nonEmptySucceededCount = 0
            )
        )

        subagentRunRepository.saveAll(
            planSteps.map { step ->
                SubagentRun(
                    id = UUID.randomUUID(),
                    briefingRunId = run.id,
                    briefingId = briefing.id,
                    personaKey = "step-${step.stepOrder}",
                    status = SubagentRunStatus.PENDING,
                    attempt = 1,
                    maxAttempts = 3,
                    createdAt = now,
                    updatedAt = now
                )
            }
        )
        synthesisRunRepository.save(
            SynthesisRun(
                id = UUID.randomUUID(),
                briefingRunId = run.id,
                status = SynthesisRunStatus.NOT_STARTED,
                createdAt = now,
                updatedAt = now
            )
        )

        briefingGenerationService.generateApprovedBriefing(briefing.id, userId)

        val allRuns = briefingRunRepository.findByBriefingIdOrderByCreatedAtDesc(briefing.id)
        assertEquals(1, allRuns.size)
        assertEquals(BriefingRunStatus.SUCCEEDED, allRuns.first().status)
    }

    @Test
    fun `active cancelling run does not dispatch additional subagent or synthesis work`() {
        val userId = insertUser("execution-orchestration-cancelling@example.com")
        val sources = listOf(createSource(userId, "source-1", "Evidence one"))
        val briefing = createApprovedBriefing(userId)
        linkSources(briefing.id, userId, sources)
        val planSteps = createPlanSteps(
            briefing.id,
            listOf("Task one", "Task two")
        )

        val now = Instant.now()
        val run = briefingRunRepository.save(
            BriefingRun(
                id = UUID.randomUUID(),
                briefingId = briefing.id,
                executionFingerprint = "prefilled-fingerprint",
                status = BriefingRunStatus.CANCELLING,
                createdAt = now,
                updatedAt = now,
                totalPersonas = planSteps.size,
                requiredForSynthesis = 1,
                nonEmptySucceededCount = 0
            )
        )

        subagentRunRepository.saveAll(
            planSteps.map { step ->
                SubagentRun(
                    id = UUID.randomUUID(),
                    briefingRunId = run.id,
                    briefingId = briefing.id,
                    personaKey = "step-${step.stepOrder}",
                    status = SubagentRunStatus.PENDING,
                    attempt = 1,
                    maxAttempts = 3,
                    createdAt = now,
                    updatedAt = now
                )
            }
        )
        synthesisRunRepository.save(
            SynthesisRun(
                id = UUID.randomUUID(),
                briefingRunId = run.id,
                status = SynthesisRunStatus.NOT_STARTED,
                createdAt = now,
                updatedAt = now
            )
        )

        assertThrows<BriefingGenerationFailedException> {
            briefingGenerationService.generateApprovedBriefing(briefing.id, userId)
        }

        val refreshedBriefing = briefingRepository.findById(briefing.id).orElseThrow()
        assertEquals(BriefingStatus.FAILED, refreshedBriefing.status)
        assertTrue(refreshedBriefing.errorJson?.contains("cancelled") == true)

        val refreshedRun = briefingRunRepository.findById(run.id).orElseThrow()
        assertEquals(BriefingRunStatus.CANCELLING, refreshedRun.status)
        assertEquals(0, refreshedRun.nonEmptySucceededCount)

        val subagents = subagentRunRepository.findByBriefingRunIdOrderByCreatedAtAsc(run.id)
        assertTrue(subagents.all { it.status == SubagentRunStatus.PENDING })

        val synthesis = synthesisRunRepository.findByBriefingRunId(run.id) ?: error("Expected synthesis run")
        assertEquals(SynthesisRunStatus.NOT_STARTED, synthesis.status)
        verify(synthesisExecutionRunner, never()).run(any())
    }

    private fun insertUser(email: String): UUID {
        val userId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO users (id, email, password_hash, role, status, auth_provider, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            userId,
            email,
            "hash",
            "USER",
            "ACTIVE",
            "LOCAL"
        )
        return userId
    }

    private fun createSource(userId: UUID, title: String, text: String): Source {
        return sourceRepository.save(
            Source(
                id = UUID.randomUUID(),
                url = Url.from("https://example.com/${UUID.randomUUID()}"),
                status = SourceStatus.ACTIVE,
                content = Content.from(text),
                metadata = Metadata.from(
                    title = title,
                    author = "author",
                    publishedDate = Instant.now(),
                    platform = "web",
                    wordCount = Content.countWords(text),
                    aiFormatted = true,
                    extractionProvider = "test"
                ),
                userId = userId,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )
    }

    private fun createApprovedBriefing(userId: UUID): Briefing {
        val now = Instant.now()
        val briefing = Briefing.create(
            id = UUID.randomUUID(),
            userId = userId,
            enrichmentIntent = BriefingEnrichmentIntent.DEEP_DIVE,
            now = now
        )
        briefing.approve(now)
        return briefingRepository.save(briefing)
    }

    private fun linkSources(briefingId: UUID, userId: UUID, sources: List<Source>) {
        briefingSourceRepository.saveAll(
            sources.map { source ->
                BriefingSource(
                    id = UUID.randomUUID(),
                    briefingId = briefingId,
                    sourceId = source.id,
                    userId = userId,
                    createdAt = Instant.now()
                )
            }
        )
    }

    private fun createPlanSteps(briefingId: UUID, tasks: List<String>): List<BriefingPlanStep> {
        return briefingPlanStepRepository.saveAll(
            tasks.mapIndexed { index, task ->
                BriefingPlanStep(
                    id = UUID.randomUUID(),
                    briefingId = briefingId,
                    personaId = null,
                    personaName = "Persona ${index + 1}",
                    stepOrder = index + 1,
                    task = task,
                    status = BriefingPlanStepStatus.PLANNED,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                )
            }
        )
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg16")
            .withDatabaseName("briefy_test")
            .withUsername("briefy")
            .withPassword("briefy")

        @JvmStatic
        @DynamicPropertySource
        fun registerDataSourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
