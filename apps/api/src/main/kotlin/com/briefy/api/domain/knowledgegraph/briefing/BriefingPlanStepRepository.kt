package com.briefy.api.domain.knowledgegraph.briefing

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface BriefingPlanStepRepository : JpaRepository<BriefingPlanStep, UUID> {
    fun findByBriefingIdOrderByStepOrderAsc(briefingId: UUID): List<BriefingPlanStep>
    fun deleteByBriefingId(briefingId: UUID)
}
