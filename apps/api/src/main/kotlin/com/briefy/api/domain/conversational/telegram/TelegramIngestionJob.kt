package com.briefy.api.domain.conversational.telegram

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "telegram_ingestion_jobs")
class TelegramIngestionJob(
    @Id
    val id: UUID,

    @Column(name = "telegram_chat_id", nullable = false)
    val telegramChatId: Long,

    @Column(name = "telegram_message_id", nullable = false)
    val telegramMessageId: Long,

    @Column(name = "telegram_user_id", nullable = false)
    val telegramUserId: Long,

    @Column(name = "linked_user_id", nullable = false)
    val linkedUserId: UUID,

    @Column(name = "payload_text", nullable = false, columnDefinition = "TEXT")
    val payloadText: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: TelegramIngestionJobStatus = TelegramIngestionJobStatus.PENDING,

    @Column(name = "attempts", nullable = false)
    var attempts: Int = 0,

    @Column(name = "max_attempts", nullable = false)
    var maxAttempts: Int = 3,

    @Column(name = "next_attempt_at", nullable = false)
    var nextAttemptAt: Instant = Instant.now(),

    @Column(name = "locked_at")
    var lockedAt: Instant? = null,

    @Column(name = "lock_owner", length = 100)
    var lockOwner: String? = null,

    @Column(name = "last_error", length = 4000)
    var lastError: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

enum class TelegramIngestionJobStatus {
    PENDING,
    PROCESSING,
    RETRY,
    SUCCEEDED,
    FAILED
}
