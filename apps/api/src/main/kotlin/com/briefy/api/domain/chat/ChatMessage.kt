package com.briefy.api.domain.chat

import com.fasterxml.jackson.databind.JsonNode
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "chat_messages")
class ChatMessage(
    @Id
    val id: UUID,

    @Column(name = "conversation_id", nullable = false)
    val conversationId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    val role: ChatMessageRole,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    val type: ChatMessageType,

    @Column(name = "content", columnDefinition = "TEXT")
    val content: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload")
    val payload: JsonNode? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", length = 20)
    val entityType: ChatEntityType? = null,

    @Column(name = "entity_id")
    val entityId: UUID? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
