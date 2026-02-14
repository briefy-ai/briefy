package com.briefy.api.domain.conversational.telegram

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface TelegramIngestionJobRepository : JpaRepository<TelegramIngestionJob, UUID> {
    fun findByTelegramChatIdAndTelegramMessageId(telegramChatId: Long, telegramMessageId: Long): TelegramIngestionJob?

    @Query(
        """
        select j.id
        from TelegramIngestionJob j
        where j.status in :statuses and j.nextAttemptAt <= :now
        order by j.nextAttemptAt asc
        """
    )
    fun findDueJobIds(
        @Param("statuses") statuses: Collection<TelegramIngestionJobStatus>,
        @Param("now") now: Instant,
        pageable: Pageable
    ): List<UUID>

    @Modifying
    @Query(
        """
        update TelegramIngestionJob j
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
        @Param("fromStatuses") fromStatuses: Collection<TelegramIngestionJobStatus>,
        @Param("newStatus") newStatus: TelegramIngestionJobStatus,
        @Param("lockedAt") lockedAt: Instant,
        @Param("lockOwner") lockOwner: String,
        @Param("now") now: Instant
    ): Int
}
