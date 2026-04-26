package com.briefy.api.application.oauthserver

import com.briefy.api.domain.identity.oauthserver.OAuthAccessGrant
import com.briefy.api.domain.identity.oauthserver.OAuthAccessGrantRepository
import com.briefy.api.domain.identity.oauthserver.OAuthAuthorizationCode
import com.briefy.api.domain.identity.oauthserver.OAuthAuthorizationCodeRepository
import com.briefy.api.domain.identity.oauthserver.OAuthClientRepository
import com.briefy.api.infrastructure.id.IdGenerator
import com.briefy.api.infrastructure.security.JwtService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID

class OAuthServerServiceTest {

    private val clientRepository = mock<OAuthClientRepository>()
    private val authCodeRepository = mock<OAuthAuthorizationCodeRepository>()
    private val accessGrantRepository = mock<OAuthAccessGrantRepository>()
    private val jwtService = JwtService(
        secret = "test-secret-should-be-at-least-32-bytes-long",
        accessTokenMinutes = 15
    )
    private val idGenerator = object : IdGenerator {
        override fun newId(): UUID = UUID.randomUUID()
    }

    private val service = OAuthServerService(
        clientRepository = clientRepository,
        authCodeRepository = authCodeRepository,
        accessGrantRepository = accessGrantRepository,
        jwtService = jwtService,
        idGenerator = idGenerator,
        accessTokenMinutes = 15,
        refreshTokenDays = 90
    )

    @Test
    fun `exchange authorization code expires refresh grant and revokes prior active grant`() {
        val userId = UUID.randomUUID()
        val previousGrant = activeGrant(userId = userId)
        val authCode = OAuthAuthorizationCode(
            id = UUID.randomUUID(),
            codeHash = "hash",
            clientId = "espriu",
            userId = userId,
            scopes = "mcp:read",
            codeChallenge = pkceChallenge("verifier"),
            redirectUri = "https://espriu.app/oauth/callback",
            expiresAt = Instant.now().plusSeconds(60)
        )
        whenever(authCodeRepository.findByCodeHash(any())).thenReturn(authCode)
        whenever(accessGrantRepository.findByUserIdAndClientIdAndRevokedAtIsNull(userId, "espriu"))
            .thenReturn(listOf(previousGrant))
        whenever(accessGrantRepository.save(any())).thenAnswer { it.arguments[0] }

        val response = service.exchangeAuthorizationCode(
            code = "code",
            clientId = "espriu",
            redirectUri = "https://espriu.app/oauth/callback",
            codeVerifier = "verifier"
        )

        val grantCaptor = argumentCaptor<OAuthAccessGrant>()
        org.mockito.kotlin.verify(accessGrantRepository).save(grantCaptor.capture())
        assertNotNull(previousGrant.revokedAt)
        assertTrue(grantCaptor.firstValue.expiresAt.isAfter(grantCaptor.firstValue.issuedAt))
        assertEquals("mcp:read", response.scope)
    }

    @Test
    fun `refresh token rejects expired grants`() {
        whenever(accessGrantRepository.findByRefreshTokenHash(any())).thenReturn(
            activeGrant(expiresAt = Instant.now().minusSeconds(1))
        )

        assertThrows<OAuthInvalidTokenException> {
            service.refreshAccessToken("refresh-token", "espriu")
        }
    }

    @Test
    fun `refresh token returns space-delimited scopes`() {
        val grant = activeGrant(scopes = "mcp:read,profile:read")
        whenever(accessGrantRepository.findByRefreshTokenHash(any())).thenReturn(grant)
        whenever(accessGrantRepository.save(any())).thenAnswer { it.arguments[0] }

        val response = service.refreshAccessToken("refresh-token", "espriu")

        assertEquals("mcp:read profile:read", response.scope)
    }

    private fun activeGrant(
        userId: UUID = UUID.randomUUID(),
        scopes: String = "mcp:read",
        expiresAt: Instant = Instant.now().plusSeconds(60)
    ) = OAuthAccessGrant(
        id = UUID.randomUUID(),
        clientId = "espriu",
        userId = userId,
        scopes = scopes,
        refreshTokenHash = "hash",
        issuedAt = Instant.now(),
        lastUsedAt = Instant.now(),
        expiresAt = expiresAt
    )

    private fun pkceChallenge(verifier: String): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
    }
}
