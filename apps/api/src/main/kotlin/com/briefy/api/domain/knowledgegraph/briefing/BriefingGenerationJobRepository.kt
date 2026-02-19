package com.briefy.api.domain.knowledgegraph.briefing

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface BriefingGenerationJobRepository : JpaRepository<BriefingGenerationJob, UUID> {
    fun findByBriefingId(briefingId: UUID): BriefingGenerationJob?

    @Query(
        """
        select j.id
        from BriefingGenerationJob j
        where j.status in :statuses
          and j.nextAttemptAt <= :now
        order by j.nextAttemptAt asc
        """
    )
    fun findDueJobIds(
        @Param("statuses") statuses: Collection<BriefingGenerationJobStatus>,
        @Param("now") now: Instant,
        pageable: Pageable
    ): List<UUID>

    @Modifying
    @Query(
        """
        update BriefingGenerationJob j
        set j.status = :newStatus,
            j.lockedAt = :lockedAt,
            j.lockOwner = :lockOwner,
            j.updatedAt = :now
        where j.id = :id
          and j.status in :fromStatuses
        """
    )
    fun markAsProcessing(
        @Param("id") id: UUID,
        @Param("fromStatuses") fromStatuses: Collection<BriefingGenerationJobStatus>,
        @Param("newStatus") newStatus: BriefingGenerationJobStatus,
        @Param("lockedAt") lockedAt: Instant,
        @Param("lockOwner") lockOwner: String,
        @Param("now") now: Instant
    ): Int
}
