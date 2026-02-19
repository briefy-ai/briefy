package com.briefy.api.domain.knowledgegraph.briefing

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface BriefingRepository : JpaRepository<Briefing, UUID> {
    fun findByIdAndUserId(id: UUID, userId: UUID): Briefing?
    fun findByUserIdOrderByUpdatedAtDesc(userId: UUID): List<Briefing>
    fun findByUserIdAndStatusOrderByUpdatedAtDesc(userId: UUID, status: BriefingStatus): List<Briefing>
}
