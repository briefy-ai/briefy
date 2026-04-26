package com.briefy.api.domain.identity.oauthserver

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "oauth_authorization_codes")
class OAuthAuthorizationCode(
    @Id
    val id: UUID,

    @Column(name = "code_hash", nullable = false, unique = true, length = 255)
    val codeHash: String,

    @Column(name = "client_id", nullable = false, length = 100)
    val clientId: String,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(nullable = false, columnDefinition = "TEXT")
    val scopes: String,

    @Column(name = "code_challenge", nullable = false, length = 255)
    val codeChallenge: String,

    @Column(name = "redirect_uri", nullable = false, columnDefinition = "TEXT")
    val redirectUri: String,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "used_at")
    var usedAt: Instant? = null
) {
    fun isValid(now: Instant): Boolean = usedAt == null && expiresAt.isAfter(now)

    fun markUsed(now: Instant) {
        usedAt = now
    }

    fun scopeList(): List<String> = scopes.split(",").map { it.trim() }.filter { it.isNotBlank() }
}
