package com.briefy.api.domain.identity.telegram

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "telegram_links")
class TelegramLink(
    @Id
    val id: UUID,

    @Column(name = "user_id", nullable = false, unique = true)
    val userId: UUID,

    @Column(name = "telegram_user_id", nullable = false, unique = true)
    var telegramUserId: Long,

    @Column(name = "telegram_chat_id", nullable = false, unique = true)
    var telegramChatId: Long,

    @Column(name = "telegram_username", length = 255)
    var telegramUsername: String? = null,

    @Column(name = "linked_at", nullable = false)
    val linkedAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
