package com.briefy.api.domain.knowledgegraph.briefing

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface BriefingSourceRepository : JpaRepository<BriefingSource, UUID> {
    fun findByBriefingIdOrderByCreatedAtAsc(briefingId: UUID): List<BriefingSource>
    fun deleteByBriefingId(briefingId: UUID)
}
