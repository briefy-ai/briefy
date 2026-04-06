package com.briefy.api.domain.knowledgegraph.briefing

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class BriefingRepositoryCustomImpl(
    @PersistenceContext private val entityManager: EntityManager
) : BriefingRepositoryCustom {

    override fun findBriefingsPage(
        userId: UUID,
        status: BriefingStatus?,
        cursorCreatedAt: Instant?,
        cursorId: UUID?,
        limit: Int
    ): List<Briefing> {
        val jpql = buildString {
            append("SELECT b FROM Briefing b WHERE b.userId = :userId")
            if (status != null) {
                append(" AND b.status = :status")
            }
            if (cursorCreatedAt != null && cursorId != null) {
                append(" AND (b.createdAt < :cursorCreatedAt OR (b.createdAt = :cursorCreatedAt AND b.id < :cursorId))")
            }
            append(" ORDER BY b.createdAt DESC, b.id DESC")
        }

        val query = entityManager.createQuery(jpql, Briefing::class.java)
            .setParameter("userId", userId)

        if (status != null) {
            query.setParameter("status", status)
        }
        if (cursorCreatedAt != null && cursorId != null) {
            query.setParameter("cursorCreatedAt", cursorCreatedAt)
            query.setParameter("cursorId", cursorId)
        }

        query.maxResults = limit + 1
        return query.resultList
    }
}
