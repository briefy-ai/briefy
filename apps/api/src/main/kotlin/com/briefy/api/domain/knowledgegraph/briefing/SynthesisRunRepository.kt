package com.briefy.api.domain.knowledgegraph.briefing

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SynthesisRunRepository : JpaRepository<SynthesisRun, UUID> {
    fun findByBriefingRunId(briefingRunId: UUID): SynthesisRun?
}
