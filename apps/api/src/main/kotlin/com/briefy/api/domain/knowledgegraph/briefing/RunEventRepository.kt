package com.briefy.api.domain.knowledgegraph.briefing

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface RunEventRepository : JpaRepository<RunEvent, UUID> {
    @Modifying
    @Query(
        value = """
            INSERT INTO run_events (
                id, event_id, briefing_run_id, subagent_run_id, event_type, occurred_at, attempt, payload_json, created_at
            ) VALUES (
                :id, :eventId, :briefingRunId, :subagentRunId, :eventType, :occurredAt, :attempt, :payloadJson, :createdAt
            )
            ON CONFLICT (event_id) DO NOTHING
        """,
        nativeQuery = true
    )
    fun insertIgnoreOnEventIdConflict(
        @Param("id") id: UUID,
        @Param("eventId") eventId: UUID,
        @Param("briefingRunId") briefingRunId: UUID,
        @Param("subagentRunId") subagentRunId: UUID?,
        @Param("eventType") eventType: String,
        @Param("occurredAt") occurredAt: Instant,
        @Param("attempt") attempt: Int?,
        @Param("payloadJson") payloadJson: String?,
        @Param("createdAt") createdAt: Instant
    ): Int

    fun existsByEventId(eventId: UUID): Boolean
    fun findByEventId(eventId: UUID): RunEvent?
    fun findByBriefingRunIdOrderByOccurredAtAscSequenceIdAsc(briefingRunId: UUID): List<RunEvent>
}
