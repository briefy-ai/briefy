package com.briefy.api.domain.knowledgegraph.source

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
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
        ORDER BY s.updatedAt DESC, s.id DESC
        """
    )
    fun findFirstPageByUserIdAndStatus(
        @Param("userId") userId: UUID,
        @Param("status") status: SourceStatus,
        pageable: Pageable
    ): List<Source>

    @Query(
        """
        SELECT s
        FROM Source s
        WHERE s.userId = :userId
          AND s.status = :status
          AND (
            s.updatedAt < :cursorUpdatedAt
            OR (s.updatedAt = :cursorUpdatedAt AND s.id < :cursorId)
          )
        ORDER BY s.updatedAt DESC, s.id DESC
        """
    )
    fun findPageByUserIdAndStatusAfterCursor(
        @Param("userId") userId: UUID,
        @Param("status") status: SourceStatus,
        @Param("cursorUpdatedAt") cursorUpdatedAt: java.time.Instant,
        @Param("cursorId") cursorId: UUID,
        pageable: Pageable
    ): List<Source>
}
