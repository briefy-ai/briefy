package com.briefy.api.domain.sharing

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "share_links")
class ShareLink(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, unique = true, length = 64)
    val token: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 32)
    val entityType: ShareLinkEntityType,

    @Column(name = "entity_id", nullable = false)
    val entityId: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "expires_at")
    val expiresAt: Instant? = null,

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
) {
    val isActive: Boolean
        get() = revokedAt == null && (expiresAt == null || Instant.now().isBefore(expiresAt))

    fun revoke() {
        revokedAt = Instant.now()
    }

    companion object {
        fun generateToken(): String = UUID.randomUUID().toString().replace("-", "")
    }
}
