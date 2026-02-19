package com.briefy.api.domain.knowledgegraph.briefing

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface BriefingReferenceRepository : JpaRepository<BriefingReference, UUID> {
    fun findByBriefingIdOrderByCreatedAtAsc(briefingId: UUID): List<BriefingReference>
    fun deleteByBriefingId(briefingId: UUID)
}
