package com.briefy.api.domain.identity.oauthserver

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "oauth_access_grants")
class OAuthAccessGrant(
    @Id
    val id: UUID,

    @Column(name = "client_id", nullable = false, length = 100)
    val clientId: String,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(nullable = false, columnDefinition = "TEXT")
    val scopes: String,

    @Column(name = "refresh_token_hash", nullable = false, unique = true, length = 255)
    val refreshTokenHash: String,

    @Column(name = "issued_at", nullable = false)
    val issuedAt: Instant,

    @Column(name = "last_used_at", nullable = false)
    var lastUsedAt: Instant,

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null
) {
    fun isActive(): Boolean = revokedAt == null

    fun revoke(now: Instant) {
        revokedAt = now
    }

    fun recordUse(now: Instant) {
        lastUsedAt = now
    }

    fun scopeList(): List<String> = scopes.split(",").map { it.trim() }.filter { it.isNotBlank() }
}
