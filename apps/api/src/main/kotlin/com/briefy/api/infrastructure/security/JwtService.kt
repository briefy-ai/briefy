package com.briefy.api.infrastructure.security

import com.briefy.api.domain.identity.user.UserRole
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Component
class JwtService(
    @Value("\${auth.jwt.secret}") private val secret: String,
    @Value("\${auth.jwt.access-token-minutes:15}") private val accessTokenMinutes: Long
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))

    fun generateAccessToken(user: AuthenticatedUser): String {
        val now = Instant.now()
        val expiresAt = now.plusSeconds(accessTokenMinutes * 60)

        return Jwts.builder()
            .subject(user.id.toString())
            .claim(CLAIM_EMAIL, user.email)
            .claim(CLAIM_ROLE, user.role.name)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(key)
            .compact()
    }

    fun parseAccessToken(token: String): AuthenticatedUser? {
        return try {
            val claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token)
                .payload
            claims.toAuthenticatedUser()
        } catch (_: Exception) {
            null
        }
    }

    fun accessTokenMaxAgeSeconds(): Long = accessTokenMinutes * 60

    private fun Claims.toAuthenticatedUser(): AuthenticatedUser {
        val userId = UUID.fromString(subject)
        val email = get(CLAIM_EMAIL, String::class.java)
        val role = UserRole.valueOf(get(CLAIM_ROLE, String::class.java))
        return AuthenticatedUser(userId, email, role)
    }

    fun generateMcpAccessToken(userId: UUID, scopes: List<String>, clientId: String): String {
        val now = Instant.now()
        val expiresAt = now.plusSeconds(accessTokenMinutes * 60)
        return Jwts.builder()
            .subject(userId.toString())
            .claim(CLAIM_SCOPE, scopes.joinToString(" "))
            .claim(CLAIM_CLIENT_ID, clientId)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(key)
            .compact()
    }

    fun parseMcpAccessToken(token: String): OAuthPrincipal? {
        return try {
            val claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token)
                .payload
            val scopeStr = claims.get(CLAIM_SCOPE, String::class.java) ?: return null
            val userId = UUID.fromString(claims.subject)
            val scopes = scopeStr.split(" ").filter { it.isNotBlank() }
            OAuthPrincipal(userId, scopes)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val CLAIM_EMAIL = "email"
        private const val CLAIM_ROLE = "role"
        private const val CLAIM_SCOPE = "scope"
        private const val CLAIM_CLIENT_ID = "client_id"
    }
}
