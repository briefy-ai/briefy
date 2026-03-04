package com.briefy.api.domain.knowledgegraph.source

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.util.UUID

@Repository
interface SourceRepository : JpaRepository<Source, UUID> {
    fun findByUserIdAndUrlNormalized(userId: UUID, normalizedUrl: String): Source?
    fun findByUserIdAndStatus(userId: UUID, status: SourceStatus): List<Source>
    fun findByUserId(userId: UUID): List<Source>
    fun findByIdAndUserId(id: UUID, userId: UUID): Source?
    fun findAllByUserIdAndIdIn(userId: UUID, ids: Collection<UUID>): List<Source>
    fun countByUrlNormalized(normalizedUrl: String): Long

    @Query(
        """
        SELECT s
        FROM Source s
        WHERE s.userId = :userId
          AND s.status = :status
          AND (
            :cursorUpdatedAt IS NULL
            OR s.updatedAt < :cursorUpdatedAt
            OR (s.updatedAt = :cursorUpdatedAt AND s.id < :cursorId)
          )
        ORDER BY s.updatedAt DESC, s.id DESC
        """
    )
    fun findPageByUserIdAndStatus(
        @Param("userId") userId: UUID,
        @Param("status") status: SourceStatus,
        @Param("cursorUpdatedAt") cursorUpdatedAt: Instant?,
        @Param("cursorId") cursorId: UUID?,
        pageable: Pageable
    ): List<Source>
}
