package com.briefy.api.application.chat

import java.time.Instant
import java.util.UUID

data class ChatContentReferenceInput(
    val id: UUID,
    val type: String
)

data class SendChatMessageCommand(
    val conversationIdOrNew: String,
    val text: String,
    val contentReferences: List<ChatContentReferenceInput>
)

data class ChatMessageResponse(
    val id: UUID,
    val role: String,
    val type: String,
    val content: String?,
    val contentReferences: List<ChatContentReferenceResponse>,
    val entityType: String?,
    val entityId: UUID?,
    val createdAt: Instant
)

data class ChatContentReferenceResponse(
    val id: UUID,
    val type: String
)

data class ChatConversationResponse(
    val id: UUID,
    val title: String?,
    val messages: List<ChatMessageResponse>,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class ChatConversationSummaryResponse(
    val id: UUID,
    val title: String?,
    val updatedAt: Instant,
    val lastMessagePreview: String?
)

data class ChatConversationPageResponse(
    val items: List<ChatConversationSummaryResponse>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val limit: Int
)

sealed interface ChatStreamEventPayload

data class ChatTokenStreamEvent(
    val type: String = "token",
    val conversationId: UUID,
    val content: String
) : ChatStreamEventPayload

data class ChatMessageStreamEvent(
    val type: String = "message",
    val conversationId: UUID,
    val message: ChatMessageResponse
) : ChatStreamEventPayload

data class ChatErrorStreamEvent(
    val type: String = "error",
    val conversationId: UUID?,
    val message: String
) : ChatStreamEventPayload
