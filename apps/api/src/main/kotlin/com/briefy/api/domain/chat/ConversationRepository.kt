package com.briefy.api.domain.chat

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ConversationRepository : JpaRepository<Conversation, UUID>, ConversationRepositoryCustom {
    fun findByIdAndUserId(id: UUID, userId: UUID): Conversation?
}
