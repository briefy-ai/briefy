package com.briefy.api.application.oauthserver

import com.briefy.api.domain.identity.oauthserver.OAuthClient
import java.util.UUID

data class AuthorizationRequestContext(
    val client: OAuthClient,
    val redirectUri: String,
    val scopes: List<String>,
    val codeChallenge: String,
    val codeChallengeMethod: String
)

data class TokenResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val refreshToken: String?,
    val scope: String
)

data class ConnectedAppInfo(
    val grantId: UUID,
    val clientId: String,
    val clientName: String,
    val scopes: List<String>,
    val issuedAt: java.time.Instant,
    val lastUsedAt: java.time.Instant
)
