package com.briefy.api.domain.chat

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ConversationRepository : JpaRepository<Conversation, UUID>, ConversationRepositoryCustom {
    fun findByIdAndUserId(id: UUID, userId: UUID): Conversation?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockByIdAndUserId(id: UUID, userId: UUID): Conversation?
}
