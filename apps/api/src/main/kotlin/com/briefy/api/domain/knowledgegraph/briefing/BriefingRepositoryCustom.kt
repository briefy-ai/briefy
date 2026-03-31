package com.briefy.api.domain.knowledgegraph.briefing

import java.time.Instant
import java.util.UUID

interface BriefingRepositoryCustom {
    fun findBriefingsPage(
        userId: UUID,
        status: BriefingStatus?,
        cursorCreatedAt: Instant?,
        cursorId: UUID?,
        limit: Int
    ): List<Briefing>
}
