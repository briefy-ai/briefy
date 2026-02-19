package com.briefy.api.domain.knowledgegraph.briefing

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "briefing_generation_jobs")
class BriefingGenerationJob(
    @Id
    val id: UUID,

    @Column(name = "briefing_id", nullable = false, unique = true)
    val briefingId: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: BriefingGenerationJobStatus = BriefingGenerationJobStatus.PENDING,

    @Column(name = "attempts", nullable = false)
    var attempts: Int = 0,

    @Column(name = "max_attempts", nullable = false)
    var maxAttempts: Int = 1,

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
