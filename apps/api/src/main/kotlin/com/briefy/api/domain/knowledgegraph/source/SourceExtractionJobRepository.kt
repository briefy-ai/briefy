package com.briefy.api.domain.knowledgegraph.source

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface SourceExtractionJobRepository : JpaRepository<SourceExtractionJob, UUID> {
    fun findBySourceId(sourceId: UUID): SourceExtractionJob?

    @Query(
        """
        select j.id
        from SourceExtractionJob j
        where j.status in :statuses and j.nextAttemptAt <= :now
        order by j.nextAttemptAt asc
        """
    )
    fun findDueJobIds(
        @Param("statuses") statuses: Collection<SourceExtractionJobStatus>,
        @Param("now") now: Instant,
        pageable: Pageable
    ): List<UUID>

    @Modifying
    @Query(
        """
        update SourceExtractionJob j
        set j.status = :newStatus,
            j.lockedAt = :lockedAt,
            j.lockOwner = :lockOwner,
            j.updatedAt = :lockedAt
        where j.id = :id
          and j.status in :fromStatuses
          and j.nextAttemptAt <= :now
        """
    )
    fun markAsProcessing(
        @Param("id") id: UUID,
        @Param("fromStatuses") fromStatuses: Collection<SourceExtractionJobStatus>,
        @Param("newStatus") newStatus: SourceExtractionJobStatus,
        @Param("lockedAt") lockedAt: Instant,
        @Param("lockOwner") lockOwner: String,
        @Param("now") now: Instant
    ): Int

    @Modifying
    @Query(
        """
        update SourceExtractionJob j
        set j.status = :retryStatus,
            j.nextAttemptAt = :now,
            j.lockedAt = null,
            j.lockOwner = null,
            j.updatedAt = :now
        where j.status = :processingStatus
          and j.lockedAt is not null
          and j.lockedAt <= :staleBefore
        """
    )
    fun reclaimStaleProcessingJobs(
        @Param("processingStatus") processingStatus: SourceExtractionJobStatus,
        @Param("retryStatus") retryStatus: SourceExtractionJobStatus,
        @Param("staleBefore") staleBefore: Instant,
        @Param("now") now: Instant
    ): Int
}
