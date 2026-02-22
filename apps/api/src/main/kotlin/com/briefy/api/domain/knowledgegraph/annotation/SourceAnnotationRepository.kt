package com.briefy.api.domain.knowledgegraph.annotation

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SourceAnnotationRepository : JpaRepository<SourceAnnotation, UUID> {
    fun findBySourceIdAndUserIdAndStatusOrderByAnchorStartAsc(
        sourceId: UUID,
        userId: UUID,
        status: SourceAnnotationStatus
    ): List<SourceAnnotation>

    fun findByIdAndSourceIdAndUserId(id: UUID, sourceId: UUID, userId: UUID): SourceAnnotation?

    @Query(
        """
        SELECT CASE WHEN COUNT(sa) > 0 THEN true ELSE false END
        FROM SourceAnnotation sa
        WHERE sa.sourceId = :sourceId
          AND sa.userId = :userId
          AND sa.status = :status
          AND sa.anchorStart < :selectionEnd
          AND sa.anchorEnd > :selectionStart
        """
    )
    fun existsOverlappingSelection(
        @Param("sourceId") sourceId: UUID,
        @Param("userId") userId: UUID,
        @Param("status") status: SourceAnnotationStatus,
        @Param("selectionStart") selectionStart: Int,
        @Param("selectionEnd") selectionEnd: Int
    ): Boolean

    fun findBySourceIdAndUserIdAndStatus(
        sourceId: UUID,
        userId: UUID,
        status: SourceAnnotationStatus
    ): List<SourceAnnotation>

    fun findBySourceIdAndUserIdAndStatusAndArchivedCause(
        sourceId: UUID,
        userId: UUID,
        status: SourceAnnotationStatus,
        archivedCause: SourceAnnotationArchiveCause
    ): List<SourceAnnotation>
}
