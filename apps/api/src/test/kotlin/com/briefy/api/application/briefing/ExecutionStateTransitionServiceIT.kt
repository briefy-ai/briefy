package com.briefy.api.application.briefing

import com.briefy.api.domain.knowledgegraph.briefing.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.Timestamp
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
        "ai.observability.enabled=false"
    ]
)
@Transactional
class ExecutionStateTransitionServiceIT {

    @Autowired
    lateinit var transitionService: ExecutionStateTransitionService

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
        jdbcTemplate.update("DELETE FROM users")
    }

    @Test
    fun `A1 and G1 - happy path transitions update run and persist event envelope`() {
        val userId = insertUser("execution-a1@example.com")
        val briefingId = insertBriefing(userId)
        val runId = insertBriefingRun(briefingId, "queued")
        val startedAt = Instant.parse("2026-03-01T10:00:00Z")
        val endedAt = Instant.parse("2026-03-01T10:00:05Z")

        transitionService.startBriefingRun(runId, UUID.randomUUID(), startedAt)
        transitionService.markBriefingRunSucceeded(runId, UUID.randomUUID(), endedAt)

        val run = briefingRunRepository.findById(runId).orElseThrow()
        assertEquals(BriefingRunStatus.SUCCEEDED, run.status)
        assertEquals(startedAt, run.startedAt)
        assertEquals(endedAt, run.endedAt)

        val events = runEventRepository.findByBriefingRunIdOrderByOccurredAtAscSequenceIdAsc(runId)
        assertEquals(2, events.size)
        assertEquals("briefing.run.started", events[0].eventType)
        assertEquals("briefing.run.completed", events[1].eventType)
        assertNotNull(events[0].eventId)
        assertNotNull(events[0].occurredAt)
        assertNotNull(events[0].payloadJson)
        assertNotNull(events[0].sequenceId)
    }

    @Test
    fun `A2 - synthesis gate failure path skips synthesis and fails briefing run`() {
        val userId = insertUser("execution-a2@example.com")
        val briefingId = insertBriefing(userId)
        val runId = insertBriefingRun(briefingId, "running")
        val synthesisId = insertSynthesisRun(runId, "not_started")
        val gateAt = Instant.parse("2026-03-01T11:00:00Z")

        transitionService.markSynthesisGateFailedSkipped(
            synthesisRunId = synthesisId,
            eventId = UUID.randomUUID(),
            requiredForSynthesis = 3,
            actualSucceeded = 2,
            occurredAt = gateAt
        )
        transitionService.markBriefingRunFailed(
            runId = runId,
            eventId = UUID.randomUUID(),
            failureCode = BriefingRunFailureCode.SYNTHESIS_GATE_NOT_MET,
            failureMessage = "gate_not_met",
            occurredAt = gateAt
        )

        val synthesis = synthesisRunRepository.findById(synthesisId).orElseThrow()
        assertEquals(SynthesisRunStatus.SKIPPED, synthesis.status)
        assertEquals(gateAt, synthesis.endedAt)

        val run = briefingRunRepository.findById(runId).orElseThrow()
        assertEquals(BriefingRunStatus.FAILED, run.status)
        assertEquals(BriefingRunFailureCode.SYNTHESIS_GATE_NOT_MET, run.failureCode)
        assertEquals(gateAt, run.endedAt)
    }

    @Test
    fun `A4 and B6 - cancellation flows propagate to retry_wait subagent and synthesis`() {
        val userId = insertUser("execution-a4@example.com")
        val briefingId = insertBriefing(userId)
        val runId = insertBriefingRun(briefingId, "running")
        val subagentId = insertSubagentRun(
            briefingRunId = runId,
            briefingId = briefingId,
            personaKey = "skeptic",
            status = "retry_wait",
            attempt = 1
        )
        val synthesisId = insertSynthesisRun(runId, "not_started")
        val now = Instant.parse("2026-03-01T12:00:00Z")

        transitionService.requestBriefingRunCancellation(runId, UUID.randomUUID(), now)
        transitionService.cancelSubagentRun(subagentId, UUID.randomUUID(), now)
        transitionService.cancelSynthesisRun(synthesisId, UUID.randomUUID(), now)
        transitionService.markBriefingRunCancelled(runId, UUID.randomUUID(), now)

        assertEquals(BriefingRunStatus.CANCELLED, briefingRunRepository.findById(runId).orElseThrow().status)
        assertEquals(SubagentRunStatus.CANCELLED, subagentRunRepository.findById(subagentId).orElseThrow().status)
        assertEquals(SynthesisRunStatus.CANCELLED, synthesisRunRepository.findById(synthesisId).orElseThrow().status)
    }

    @Test
    fun `B1 - subagent running to succeeded with non-empty output`() {
        val userId = insertUser("execution-b1@example.com")
        val briefingId = insertBriefing(userId)
        val runId = insertBriefingRun(briefingId, "running")
        val subagentId = insertSubagentRun(
            briefingRunId = runId,
            briefingId = briefingId,
            personaKey = "scientist",
            status = "pending",
            attempt = 1
        )
        val now = Instant.parse("2026-03-01T13:00:00Z")

        transitionService.dispatchSubagentRun(subagentId, UUID.randomUUID(), now)
        transitionService.markSubagentCompletedNonEmpty(
            subagentRunId = subagentId,
            eventId = UUID.randomUUID(),
            curatedText = "Evidence-backed result",
            occurredAt = now.plusSeconds(5)
        )

        val subagent = subagentRunRepository.findById(subagentId).orElseThrow()
        assertEquals(SubagentRunStatus.SUCCEEDED, subagent.status)
        assertEquals("Evidence-backed result", subagent.curatedText)
        assertNotNull(subagent.endedAt)
    }

    @Test
    fun `B2 - subagent running to skipped_no_output`() {
        val userId = insertUser("execution-b2@example.com")
        val briefingId = insertBriefing(userId)
        val runId = insertBriefingRun(briefingId, "running")
        val subagentId = insertSubagentRun(
            briefingRunId = runId,
            briefingId = briefingId,
            personaKey = "economist",
            status = "running",
            attempt = 1
        )

        transitionService.markSubagentCompletedEmpty(subagentId, UUID.randomUUID(), occurredAt = Instant.now())

        val subagent = subagentRunRepository.findById(subagentId).orElseThrow()
        assertEquals(SubagentRunStatus.SKIPPED_NO_OUTPUT, subagent.status)
        assertNotNull(subagent.endedAt)
    }

    @Test
    fun `B5 and G4 - non-retryable failure marks failed and emits attempt correlated event`() {
        val userId = insertUser("execution-b5@example.com")
        val briefingId = insertBriefing(userId)
        val runId = insertBriefingRun(briefingId, "running")
        val subagentId = insertSubagentRun(
            briefingRunId = runId,
            briefingId = briefingId,
            personaKey = "auditor",
            status = "running",
            attempt = 2
        )

        transitionService.markSubagentNonRetryableFailed(
            subagentRunId = subagentId,
            eventId = UUID.randomUUID(),
            errorCode = "validation_error",
            errorMessage = "schema mismatch",
            occurredAt = Instant.now()
        )

        val subagent = subagentRunRepository.findById(subagentId).orElseThrow()
        assertEquals(SubagentRunStatus.FAILED, subagent.status)
        assertEquals(false, subagent.lastErrorRetryable)
        assertEquals("validation_error", subagent.lastErrorCode)

        val event = runEventRepository.findByBriefingRunIdOrderByOccurredAtAscSequenceIdAsc(runId)
            .first { it.eventType == "subagent.failed" }
        assertEquals(subagentId, event.subagentRunId)
        assertEquals(2, event.attempt)
    }

    @Test
    fun `G4 - retry lifecycle events carry evolving attempt`() {
        val userId = insertUser("execution-g4@example.com")
        val briefingId = insertBriefing(userId)
        val runId = insertBriefingRun(briefingId, "running")
        val subagentId = insertSubagentRun(
            briefingRunId = runId,
            briefingId = briefingId,
            personaKey = "progressive",
            status = "running",
            attempt = 1
        )
        val now = Instant.parse("2026-03-01T14:00:00Z")

        transitionService.markSubagentTransientFailedToRetryWait(
            subagentRunId = subagentId,
            eventId = UUID.randomUUID(),
            errorCode = "http_429",
            errorMessage = "retry",
            occurredAt = now
        )
        transitionService.markSubagentRetryDelayElapsed(
            subagentRunId = subagentId,
            eventId = UUID.randomUUID(),
            occurredAt = now.plusSeconds(1)
        )
        transitionService.markSubagentNonRetryableFailed(
            subagentRunId = subagentId,
            eventId = UUID.randomUUID(),
            errorCode = "prompt_schema_error",
            errorMessage = "invalid payload",
            occurredAt = now.plusSeconds(2)
        )

        val events = runEventRepository.findByBriefingRunIdOrderByOccurredAtAscSequenceIdAsc(runId)
            .filter { it.subagentRunId == subagentId }
        assertTrue(events.any { it.eventType == "subagent.retry.scheduled" && it.attempt == 1 })
        assertTrue(events.any { it.eventType == "subagent.dispatched" && it.attempt == 2 })
        assertTrue(events.any { it.eventType == "subagent.failed" && it.attempt == 2 })
    }

    @Test
    fun `idempotent event_id write is no-op on duplicate`() {
        val userId = insertUser("execution-idempotent@example.com")
        val briefingId = insertBriefing(userId)
        val runId = insertBriefingRun(briefingId, "queued")
        val eventId = UUID.randomUUID()
        val now = Instant.parse("2026-03-01T15:00:00Z")

        transitionService.startBriefingRun(runId, eventId, now)
        transitionService.startBriefingRun(runId, eventId, now.plusSeconds(1))

        val run = briefingRunRepository.findById(runId).orElseThrow()
        assertEquals(BriefingRunStatus.RUNNING, run.status)

        val events = runEventRepository.findByBriefingRunIdOrderByOccurredAtAscSequenceIdAsc(runId)
        assertEquals(1, events.size)
        assertEquals("briefing.run.started", events.first().eventType)
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

    private fun insertBriefing(userId: UUID): UUID {
        val briefingId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO briefings (id, user_id, created_at, updated_at)
            VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """.trimIndent(),
            briefingId,
            userId
        )
        return briefingId
    }

    private fun insertBriefingRun(briefingId: UUID, status: String): UUID {
        val runId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO briefing_runs (
                id, briefing_id, execution_fingerprint, status, total_personas, required_for_synthesis
            ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            runId,
            briefingId,
            "fp-${UUID.randomUUID()}",
            status,
            5,
            3
        )
        return runId
    }

    private fun insertSubagentRun(
        briefingRunId: UUID,
        briefingId: UUID,
        personaKey: String,
        status: String,
        attempt: Int
    ): UUID {
        val subagentId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO subagent_runs (
                id, briefing_run_id, briefing_id, persona_key, status, attempt, max_attempts, started_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            subagentId,
            briefingRunId,
            briefingId,
            personaKey,
            status,
            attempt,
            3,
            Timestamp.from(Instant.parse("2026-03-01T09:00:00Z"))
        )
        return subagentId
    }

    private fun insertSynthesisRun(briefingRunId: UUID, status: String): UUID {
        val synthesisId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO synthesis_runs (id, briefing_run_id, status)
            VALUES (?, ?, ?)
            """.trimIndent(),
            synthesisId,
            briefingRunId,
            status
        )
        return synthesisId
    }

    companion object {
        @Container
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("pgvector/pgvector:pg16")
            .withDatabaseName("briefy")
            .withUsername("briefy")
            .withPassword("briefy")

        @JvmStatic
        @DynamicPropertySource
        fun datasourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }
}
