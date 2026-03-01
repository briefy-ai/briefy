package com.briefy.api.domain.knowledgegraph.briefing

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface BriefingRunRepository : JpaRepository<BriefingRun, UUID> {
    fun findByBriefingIdOrderByCreatedAtDesc(briefingId: UUID): List<BriefingRun>
    fun findTopByBriefingIdAndStatusInOrderByCreatedAtDesc(
        briefingId: UUID,
        statuses: Collection<BriefingRunStatus>
    ): BriefingRun?

    fun findTopByBriefingIdOrderByCreatedAtDesc(briefingId: UUID): BriefingRun?
}
