package com.briefy.api.infrastructure.briefing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
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
class ExecutionPersistenceConstraintsIT {

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
    fun `enforces single active briefing run per briefing`() {
        val userId = insertUser("active-run@example.com")
        val briefingId = insertBriefing(userId)

        insertBriefingRun(briefingId = briefingId, status = "queued")

        assertThrows<DataIntegrityViolationException> {
            insertBriefingRun(briefingId = briefingId, status = "running")
        }
    }

    @Test
    fun `allows new active run when prior run is terminal`() {
        val userId = insertUser("terminal-run@example.com")
        val briefingId = insertBriefing(userId)

        insertBriefingRun(
            briefingId = briefingId,
            status = "succeeded",
            endedAt = Instant.parse("2026-02-28T10:00:00Z")
        )
        insertBriefingRun(briefingId = briefingId, status = "queued")

        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM briefing_runs WHERE briefing_id = ?",
            Int::class.java,
            briefingId
        )
        assertEquals(2, count)
    }

    @Test
    fun `enforces one subagent persona per run`() {
        val userId = insertUser("subagent-unique@example.com")
        val briefingId = insertBriefing(userId)
        val runId = insertBriefingRun(briefingId = briefingId, status = "running")

        insertSubagentRun(
            briefingRunId = runId,
            briefingId = briefingId,
            personaKey = "scientist",
            status = "running"
        )

        assertThrows<DataIntegrityViolationException> {
            insertSubagentRun(
                briefingRunId = runId,
                briefingId = briefingId,
                personaKey = "scientist",
                status = "running"
            )
        }
    }

    @Test
    fun `enforces single synthesis row per run`() {
        val userId = insertUser("synthesis-unique@example.com")
        val briefingId = insertBriefing(userId)
        val runId = insertBriefingRun(briefingId = briefingId, status = "running")

        insertSynthesisRun(briefingRunId = runId, status = "not_started")

        assertThrows<DataIntegrityViolationException> {
            insertSynthesisRun(briefingRunId = runId, status = "running")
        }
    }

    @Test
    fun `enforces unique event_id for idempotent event writes`() {
        val userId = insertUser("event-id@example.com")
        val briefingId = insertBriefing(userId)
        val runId = insertBriefingRun(briefingId = briefingId, status = "running")
        val eventId = UUID.randomUUID()
        val occurredAt = Instant.parse("2026-02-28T10:00:00Z")

        insertRunEvent(
            briefingRunId = runId,
            eventId = eventId,
            eventType = "briefing.run.started",
            occurredAt = occurredAt
        )

        assertThrows<DataIntegrityViolationException> {
            insertRunEvent(
                briefingRunId = runId,
                eventId = eventId,
                eventType = "briefing.run.started",
                occurredAt = occurredAt.plusSeconds(1)
            )
        }
    }

    @Test
    fun `orders events by occurred_at and sequence_id`() {
        val userId = insertUser("event-order@example.com")
        val briefingId = insertBriefing(userId)
        val runId = insertBriefingRun(briefingId = briefingId, status = "running")
        val sameTs = Instant.parse("2026-02-28T10:00:00Z")

        insertRunEvent(runId, UUID.randomUUID(), "subagent.dispatched", sameTs)
        insertRunEvent(runId, UUID.randomUUID(), "subagent.tool_call.started", sameTs)
        insertRunEvent(runId, UUID.randomUUID(), "subagent.tool_call.completed", sameTs)

        val rows = listEventsOrdered(runId)
        assertEquals(3, rows.size)
        assertTrue(rows[0].sequenceId < rows[1].sequenceId)
        assertTrue(rows[1].sequenceId < rows[2].sequenceId)
    }

    @Test
    fun `cursor pagination returns continuous event stream without duplicates`() {
        val userId = insertUser("event-cursor@example.com")
        val briefingId = insertBriefing(userId)
        val runId = insertBriefingRun(briefingId = briefingId, status = "running")
        val t0 = Instant.parse("2026-02-28T10:00:00Z")

        insertRunEvent(runId, UUID.randomUUID(), "e1", t0)
        insertRunEvent(runId, UUID.randomUUID(), "e2", t0)
        insertRunEvent(runId, UUID.randomUUID(), "e3", t0.plusSeconds(1))
        insertRunEvent(runId, UUID.randomUUID(), "e4", t0.plusSeconds(1))
        insertRunEvent(runId, UUID.randomUUID(), "e5", t0.plusSeconds(2))

        val fullOrder = listEventsOrdered(runId)
        val paged = mutableListOf<EventRow>()
        var cursorTs: Instant? = null
        var cursorSeq: Long? = null

        while (true) {
            val page = listEventsPage(
                briefingRunId = runId,
                cursorOccurredAt = cursorTs,
                cursorSequenceId = cursorSeq,
                limit = 2
            )
            if (page.isEmpty()) {
                break
            }
            paged.addAll(page)
            val last = page.last()
            cursorTs = last.occurredAt
            cursorSeq = last.sequenceId
        }

        assertEquals(fullOrder.map { it.id }, paged.map { it.id })
        assertEquals(paged.size, paged.map { it.id }.toSet().size)
    }

    @Test
    fun `enforces briefing run terminal ended_at invariant`() {
        val userId = insertUser("checks@example.com")
        val briefingId = insertBriefing(userId)

        assertThrows<DataIntegrityViolationException> {
            insertBriefingRun(briefingId = briefingId, status = "failed", endedAt = null)
        }
    }

    @Test
    fun `enforces subagent attempt bounds`() {
        val userId = insertUser("checks-subagent@example.com")
        val briefingId = insertBriefing(userId)
        val runId = insertBriefingRun(briefingId = briefingId, status = "running")

        assertThrows<DataIntegrityViolationException> {
            insertSubagentRun(
                briefingRunId = runId,
                briefingId = briefingId,
                personaKey = "skeptic",
                status = "running",
                attempt = 0,
                maxAttempts = 3
            )
        }
    }

    @Test
    fun `enforces synthesis terminal ended_at invariant`() {
        val userId = insertUser("checks-synthesis@example.com")
        val briefingId = insertBriefing(userId)
        val runId = insertBriefingRun(briefingId = briefingId, status = "running")

        assertThrows<DataIntegrityViolationException> {
            insertSynthesisRun(
                briefingRunId = runId,
                status = "failed",
                endedAt = null
            )
        }
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

    private fun insertBriefingRun(
        briefingId: UUID,
        status: String,
        endedAt: Instant? = null
    ): UUID {
        val runId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO briefing_runs (
                id, briefing_id, execution_fingerprint, status, ended_at, total_personas, required_for_synthesis
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            runId,
            briefingId,
            "fp-${UUID.randomUUID()}",
            status,
            endedAt?.let { Timestamp.from(it) },
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
        attempt: Int = 1,
        maxAttempts: Int = 3,
        endedAt: Instant? = null
    ): UUID {
        val subagentRunId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO subagent_runs (
                id, briefing_run_id, briefing_id, persona_key, status, attempt, max_attempts, ended_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            subagentRunId,
            briefingRunId,
            briefingId,
            personaKey,
            status,
            attempt,
            maxAttempts,
            endedAt?.let { Timestamp.from(it) }
        )
        return subagentRunId
    }

    private fun insertSynthesisRun(
        briefingRunId: UUID,
        status: String,
        endedAt: Instant? = null
    ): UUID {
        val synthesisRunId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO synthesis_runs (id, briefing_run_id, status, ended_at)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
            synthesisRunId,
            briefingRunId,
            status,
            endedAt?.let { Timestamp.from(it) }
        )
        return synthesisRunId
    }

    private fun insertRunEvent(
        briefingRunId: UUID,
        eventId: UUID,
        eventType: String,
        occurredAt: Instant,
        subagentRunId: UUID? = null
    ): UUID {
        val rowId = UUID.randomUUID()
        jdbcTemplate.update(
            """
            INSERT INTO run_events (id, event_id, briefing_run_id, subagent_run_id, event_type, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            rowId,
            eventId,
            briefingRunId,
            subagentRunId,
            eventType,
            Timestamp.from(occurredAt)
        )
        return rowId
    }

    private fun listEventsOrdered(briefingRunId: UUID): List<EventRow> {
        return jdbcTemplate.query(
            """
            SELECT id, occurred_at, sequence_id
            FROM run_events
            WHERE briefing_run_id = ?
            ORDER BY occurred_at ASC, sequence_id ASC
            """.trimIndent(),
            { rs, _ ->
                EventRow(
                    id = UUID.fromString(rs.getString("id")),
                    occurredAt = rs.getTimestamp("occurred_at").toInstant(),
                    sequenceId = rs.getLong("sequence_id")
                )
            },
            briefingRunId
        )
    }

    private fun listEventsPage(
        briefingRunId: UUID,
        cursorOccurredAt: Instant?,
        cursorSequenceId: Long?,
        limit: Int
    ): List<EventRow> {
        if (cursorOccurredAt == null || cursorSequenceId == null) {
            return jdbcTemplate.query(
                """
                SELECT id, occurred_at, sequence_id
                FROM run_events
                WHERE briefing_run_id = ?
                ORDER BY occurred_at ASC, sequence_id ASC
                LIMIT ?
                """.trimIndent(),
                { rs, _ ->
                    EventRow(
                        id = UUID.fromString(rs.getString("id")),
                        occurredAt = rs.getTimestamp("occurred_at").toInstant(),
                        sequenceId = rs.getLong("sequence_id")
                    )
                },
                briefingRunId,
                limit
            )
        }

        return jdbcTemplate.query(
            """
            SELECT id, occurred_at, sequence_id
            FROM run_events
            WHERE briefing_run_id = ?
              AND (occurred_at > ? OR (occurred_at = ? AND sequence_id > ?))
            ORDER BY occurred_at ASC, sequence_id ASC
            LIMIT ?
            """.trimIndent(),
            { rs, _ ->
                EventRow(
                    id = UUID.fromString(rs.getString("id")),
                    occurredAt = rs.getTimestamp("occurred_at").toInstant(),
                    sequenceId = rs.getLong("sequence_id")
                )
            },
            briefingRunId,
            Timestamp.from(cursorOccurredAt),
            Timestamp.from(cursorOccurredAt),
            cursorSequenceId,
            limit
        )
    }

    private data class EventRow(
        val id: UUID,
        val occurredAt: Instant,
        val sequenceId: Long
    )

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
