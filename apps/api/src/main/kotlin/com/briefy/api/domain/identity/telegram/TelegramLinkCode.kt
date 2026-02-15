package com.briefy.api.domain.identity.telegram

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "telegram_link_codes")
class TelegramLinkCode(
    @Id
    val id: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "code_hash", nullable = false, unique = true, length = 128)
    val codeHash: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "expires_at")
    val expiresAt: Instant? = null,

    @Column(name = "used_at")
    var usedAt: Instant? = null
)
