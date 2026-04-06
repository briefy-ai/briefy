package com.briefy.api.domain.chat

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class ConversationRepositoryCustomImpl(
    @PersistenceContext private val entityManager: EntityManager
) : ConversationRepositoryCustom {

    override fun findConversationPage(
        userId: UUID,
        cursorUpdatedAt: Instant?,
        cursorId: UUID?,
        limit: Int
    ): List<Conversation> {
        val jpql = buildString {
            append("SELECT c FROM Conversation c WHERE c.userId = :userId")
            if (cursorUpdatedAt != null && cursorId != null) {
                append(" AND (c.updatedAt < :cursorUpdatedAt OR (c.updatedAt = :cursorUpdatedAt AND c.id < :cursorId))")
            }
            append(" ORDER BY c.updatedAt DESC, c.id DESC")
        }

        val query = entityManager.createQuery(jpql, Conversation::class.java)
            .setParameter("userId", userId)

        if (cursorUpdatedAt != null && cursorId != null) {
            query.setParameter("cursorUpdatedAt", cursorUpdatedAt)
            query.setParameter("cursorId", cursorId)
        }

        query.maxResults = limit + 1
        return query.resultList
    }
}
