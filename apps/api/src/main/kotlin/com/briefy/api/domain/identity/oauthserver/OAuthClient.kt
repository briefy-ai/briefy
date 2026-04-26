package com.briefy.api.domain.identity.oauthserver

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "oauth_clients")
class OAuthClient(
    @Id
    val id: UUID,

    @Column(name = "client_id", nullable = false, unique = true, length = 100)
    val clientId: String,

    @Column(nullable = false, length = 255)
    val name: String,

    @Column(name = "allowed_redirect_uris", nullable = false, columnDefinition = "TEXT")
    val allowedRedirectUris: String,

    @Column(name = "allowed_scopes", nullable = false, columnDefinition = "TEXT")
    val allowedScopes: String,

    @Column(name = "require_pkce", nullable = false)
    val requirePkce: Boolean,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant
) {
    fun redirectUriList(): List<String> = allowedRedirectUris.split(",").map { it.trim() }

    fun scopeList(): List<String> = allowedScopes.split(",").map { it.trim() }

    fun allowsRedirectUri(uri: String): Boolean = redirectUriList().any { it == uri }

    fun allowsScope(scope: String): Boolean = scopeList().any { it == scope }
}
