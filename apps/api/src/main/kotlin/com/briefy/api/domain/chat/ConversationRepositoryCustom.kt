package com.briefy.api.domain.chat

import java.time.Instant
import java.util.UUID

interface ConversationRepositoryCustom {
    fun findConversationPage(
        userId: UUID,
        cursorUpdatedAt: Instant?,
        cursorId: UUID?,
        limit: Int
    ): List<Conversation>
}
