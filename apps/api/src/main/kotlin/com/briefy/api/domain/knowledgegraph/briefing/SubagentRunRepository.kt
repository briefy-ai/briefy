package com.briefy.api.domain.knowledgegraph.briefing

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SubagentRunRepository : JpaRepository<SubagentRun, UUID> {
    fun existsByIdAndBriefingRunId(id: UUID, briefingRunId: UUID): Boolean
    fun findByBriefingRunIdOrderByCreatedAtAsc(briefingRunId: UUID): List<SubagentRun>

    @Modifying
    @Query("DELETE FROM SubagentRun s WHERE s.briefingRunId IN (SELECT r.id FROM BriefingRun r WHERE r.briefingId = :briefingId)")
    fun deleteByBriefingId(@Param("briefingId") briefingId: UUID)
}
