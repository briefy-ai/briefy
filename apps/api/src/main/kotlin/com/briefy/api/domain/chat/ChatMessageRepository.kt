package com.briefy.api.domain.chat

import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ChatMessageRepository : JpaRepository<ChatMessage, UUID> {
    fun findByConversationIdOrderByCreatedAtAsc(conversationId: UUID): List<ChatMessage>
    fun findTopByConversationIdOrderByCreatedAtDesc(conversationId: UUID): ChatMessage?
    @Query(
        """
        select m.conversationId as conversationId, m.content as content
        from ChatMessage m
        where m.conversationId in :conversationIds
          and m.content is not null
          and trim(m.content) <> ''
          and m.createdAt = (
            select max(latest.createdAt)
            from ChatMessage latest
            where latest.conversationId = m.conversationId
              and latest.content is not null
              and trim(latest.content) <> ''
          )
        """
    )
    fun findLatestContentByConversationIds(conversationIds: Collection<UUID>): List<ChatConversationPreviewProjection>
    fun deleteByConversationId(conversationId: UUID)
}

interface ChatConversationPreviewProjection {
    val conversationId: UUID
    val content: String?
}
