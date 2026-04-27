package com.briefy.api.domain.knowledgegraph.briefing

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface BriefingSourceRepository : JpaRepository<BriefingSource, UUID> {
    fun findByBriefingIdOrderByCreatedAtAsc(briefingId: UUID): List<BriefingSource>
    fun countByBriefingId(briefingId: UUID): Int
    fun deleteByBriefingId(briefingId: UUID)
    fun findByUserIdAndSourceId(userId: UUID, sourceId: UUID): List<BriefingSource>

    @Query(
        """
        select bs.briefingId as briefingId
        from BriefingSource bs
        join Briefing b on b.id = bs.briefingId and b.userId = bs.userId
        where bs.userId = :userId
          and bs.sourceId = :sourceId
          and b.status = :briefingStatus
        order by bs.createdAt asc
        """
    )
    fun findBriefingIdsByUserAndSourceAndStatus(
        @Param("userId") userId: UUID,
        @Param("sourceId") sourceId: UUID,
        @Param("briefingStatus") briefingStatus: BriefingStatus
    ): List<BriefingIdProjection>

    fun findByBriefingIdInOrderByCreatedAtAsc(briefingIds: Collection<UUID>): List<BriefingSource>
}

interface BriefingIdProjection {
    val briefingId: UUID
}
