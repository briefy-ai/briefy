package com.briefy.api.domain.chat

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ChatMessageRepository : JpaRepository<ChatMessage, UUID> {
    fun findByConversationIdOrderByCreatedAtAsc(conversationId: UUID): List<ChatMessage>
    fun findTopByConversationIdOrderByCreatedAtDesc(conversationId: UUID): ChatMessage?
    fun deleteByConversationId(conversationId: UUID)
}
