package com.briefy.api.domain.knowledgegraph.briefing

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface RunEventRepository : JpaRepository<RunEvent, UUID> {
    fun existsByEventId(eventId: UUID): Boolean
    fun findByEventId(eventId: UUID): RunEvent?
    fun findByBriefingRunIdOrderByOccurredAtAscSequenceIdAsc(briefingRunId: UUID): List<RunEvent>
}
