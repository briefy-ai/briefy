package com.briefy.api.application.oauthserver

import com.briefy.api.domain.identity.oauthserver.OAuthAccessGrant
import com.briefy.api.domain.identity.oauthserver.OAuthAccessGrantRepository
import com.briefy.api.domain.identity.oauthserver.OAuthAuthorizationCode
import com.briefy.api.domain.identity.oauthserver.OAuthAuthorizationCodeRepository
import com.briefy.api.domain.identity.oauthserver.OAuthClientRepository
import com.briefy.api.infrastructure.id.IdGenerator
import com.briefy.api.infrastructure.security.JwtService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class OAuthServerService(
    private val clientRepository: OAuthClientRepository,
    private val authCodeRepository: OAuthAuthorizationCodeRepository,
    private val accessGrantRepository: OAuthAccessGrantRepository,
    private val jwtService: JwtService,
    private val idGenerator: IdGenerator,
    @param:Value("\${auth.jwt.access-token-minutes:15}") private val accessTokenMinutes: Long,
    @param:Value("\${oauth.server.refresh-token-days:90}") private val refreshTokenDays: Long
) {
    private val logger = LoggerFactory.getLogger(OAuthServerService::class.java)
    private val secureRandom = SecureRandom()

    @Transactional(readOnly = true)
    fun validateAuthorizationRequest(
        clientId: String,
        redirectUri: String,
        scope: String,
        codeChallenge: String?,
        codeChallengeMethod: String?
    ): AuthorizationRequestContext {
        val client = clientRepository.findByClientId(clientId)
            ?: throw OAuthClientNotFoundException(clientId)

        if (!client.allowsRedirectUri(redirectUri)) {
            throw OAuthInvalidRedirectUriException(redirectUri)
        }

        val requestedScopes = scope.split(" ").filter { it.isNotBlank() }
        requestedScopes.forEach { s ->
            if (!client.allowsScope(s)) throw OAuthInvalidScopeException(s)
        }

        if (client.requirePkce) {
            if (codeChallenge.isNullOrBlank()) throw OAuthPkceRequiredException()
            if (codeChallengeMethod != "S256") {
                throw OAuthInvalidRequestException("Unsupported code_challenge_method: only S256 is supported")
            }
        }

        return AuthorizationRequestContext(
            client = client,
            redirectUri = redirectUri,
            scopes = requestedScopes,
            codeChallenge = codeChallenge ?: "",
            codeChallengeMethod = codeChallengeMethod ?: "S256"
        )
    }

    @Transactional
    fun issueAuthorizationCode(
        clientId: String,
        userId: UUID,
        redirectUri: String,
        scopes: List<String>,
        codeChallenge: String
    ): String {
        val plainCode = generateOpaqueToken()
        val codeHash = hashToken(plainCode)
        val now = Instant.now()

        val code = OAuthAuthorizationCode(
            id = idGenerator.newId(),
            codeHash = codeHash,
            clientId = clientId,
            userId = userId,
            scopes = scopes.joinToString(","),
            codeChallenge = codeChallenge,
            redirectUri = redirectUri,
            expiresAt = now.plusSeconds(60)
        )
        authCodeRepository.save(code)
        logger.info("[oauth] Authorization code issued clientId={} userId={}", clientId, userId)
        return plainCode
    }

    @Transactional
    fun exchangeAuthorizationCode(
        code: String,
        clientId: String,
        redirectUri: String,
        codeVerifier: String
    ): TokenResponse {
        val codeHash = hashToken(code)
        val authCode = authCodeRepository.findByCodeHash(codeHash)
            ?: throw OAuthInvalidGrantException()

        val now = Instant.now()
        if (!authCode.isValid(now)) throw OAuthInvalidGrantException("Authorization code expired or already used")
        if (authCode.clientId != clientId) throw OAuthInvalidGrantException("client_id mismatch")
        if (authCode.redirectUri != redirectUri) throw OAuthInvalidGrantException("redirect_uri mismatch")
        if (!verifyPkce(codeVerifier, authCode.codeChallenge)) throw OAuthPkceVerificationException()

        authCode.markUsed(now)
        authCodeRepository.save(authCode)

        return issueTokens(authCode.userId, clientId, authCode.scopeList(), now)
    }

    @Transactional
    fun refreshAccessToken(refreshToken: String, clientId: String): TokenResponse {
        val tokenHash = hashToken(refreshToken)
        val grant = accessGrantRepository.findByRefreshTokenHash(tokenHash)
            ?: throw OAuthInvalidTokenException()

        val now = Instant.now()
        if (!grant.isActive(now)) throw OAuthInvalidTokenException("Token has been revoked or expired")
        if (grant.clientId != clientId) throw OAuthInvalidTokenException("client_id mismatch")

        grant.recordUse(now)
        accessGrantRepository.save(grant)

        val accessToken = jwtService.generateMcpAccessToken(grant.userId, grant.scopeList(), clientId)
        logger.info("[oauth] Access token refreshed clientId={} userId={}", clientId, grant.userId)
        return TokenResponse(
            accessToken = accessToken,
            expiresIn = accessTokenMinutes * 60,
            refreshToken = null,
            scope = grant.scopeList().joinToString(" ")
        )
    }

    @Transactional
    fun revokeByRefreshToken(refreshToken: String) {
        val tokenHash = hashToken(refreshToken)
        val grant = accessGrantRepository.findByRefreshTokenHash(tokenHash) ?: return
        grant.revoke(Instant.now())
        accessGrantRepository.save(grant)
        logger.info("[oauth] Grant revoked clientId={} userId={}", grant.clientId, grant.userId)
    }

    @Transactional(readOnly = true)
    fun listActiveGrants(userId: UUID): List<ConnectedAppInfo> {
        val grants = accessGrantRepository.findActiveByUserId(userId, Instant.now())
        val clientIds = grants.map { it.clientId }.distinct()
        val clients = clientIds.mapNotNull { clientRepository.findByClientId(it) }.associateBy { it.clientId }

        return grants.map { grant ->
            val client = clients[grant.clientId]
            ConnectedAppInfo(
                grantId = grant.id,
                clientId = grant.clientId,
                clientName = client?.name ?: grant.clientId,
                scopes = grant.scopeList(),
                issuedAt = grant.issuedAt,
                lastUsedAt = grant.lastUsedAt
            )
        }
    }

    @Transactional
    fun revokeGrant(grantId: UUID, userId: UUID) {
        val grant = accessGrantRepository.findById(grantId).orElseThrow {
            OAuthInvalidTokenException("Grant not found")
        }
        if (grant.userId != userId) throw OAuthInvalidTokenException("Grant not found")
        grant.revoke(Instant.now())
        accessGrantRepository.save(grant)
        logger.info("[oauth] Grant revoked by user grantId={} userId={}", grantId, userId)
    }

    private fun issueTokens(userId: UUID, clientId: String, scopes: List<String>, now: Instant): TokenResponse {
        val refreshToken = generateOpaqueToken()
        val tokenHash = hashToken(refreshToken)

        accessGrantRepository.findByUserIdAndClientIdAndRevokedAtIsNull(userId, clientId)
            .forEach { it.revoke(now) }

        val grant = OAuthAccessGrant(
            id = idGenerator.newId(),
            clientId = clientId,
            userId = userId,
            scopes = scopes.joinToString(","),
            refreshTokenHash = tokenHash,
            issuedAt = now,
            lastUsedAt = now,
            expiresAt = now.plusSeconds(refreshTokenDays * SECONDS_PER_DAY)
        )
        accessGrantRepository.save(grant)

        val accessToken = jwtService.generateMcpAccessToken(userId, scopes, clientId)
        logger.info("[oauth] Tokens issued clientId={} userId={}", clientId, userId)
        return TokenResponse(
            accessToken = accessToken,
            expiresIn = accessTokenMinutes * 60,
            refreshToken = refreshToken,
            scope = scopes.joinToString(" ")
        )
    }

    private fun verifyPkce(codeVerifier: String, codeChallenge: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        val computed = Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
        return computed == codeChallenge
    }

    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(token.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(hash)
    }

    private fun generateOpaqueToken(): String {
        val bytes = ByteArray(48)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        const val SECONDS_PER_DAY = 24 * 60 * 60L
    }
}
