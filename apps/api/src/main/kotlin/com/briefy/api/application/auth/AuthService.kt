package com.briefy.api.application.auth

import com.briefy.api.domain.identity.session.RefreshSession
import com.briefy.api.domain.identity.session.RefreshSessionRepository
import com.briefy.api.domain.identity.user.User
import com.briefy.api.domain.identity.user.UserRepository
import com.briefy.api.infrastructure.id.IdGenerator
import com.briefy.api.infrastructure.security.AuthenticatedUser
import com.briefy.api.infrastructure.security.JwtService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshSessionRepository: RefreshSessionRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val idGenerator: IdGenerator,
    @Value("\${auth.refresh-token.days:30}") private val refreshTokenDays: Long
) {
    private val logger = LoggerFactory.getLogger(AuthService::class.java)
    private val secureRandom = SecureRandom()

    @Transactional
    fun signUp(command: SignUpCommand): AuthResult {
        val email = normalizeEmail(command.email)
        logger.info("[service] Signing up user email={}", email)
        if (userRepository.existsByEmail(email)) {
            throw EmailAlreadyExistsException(email)
        }

        val user = User.createLocal(
            id = idGenerator.newId(),
            email = email,
            passwordHash = requireNotNull(passwordEncoder.encode(command.password)) {
                "Password encoder returned null hash"
            },
            displayName = command.displayName
        )
        userRepository.save(user)
        logger.info("[service] User signed up userId={}", user.id)
        return issueTokens(user)
    }

    @Transactional
    fun login(command: LoginCommand): AuthResult {
        val email = normalizeEmail(command.email)
        logger.info("[service] Login attempt email={}", email)
        val user = userRepository.findByEmail(email) ?: throw InvalidCredentialsException()
        if (!passwordEncoder.matches(command.password, user.passwordHash)) {
            throw InvalidCredentialsException()
        }
        user.ensureActive()
        logger.info("[service] Login successful userId={}", user.id)
        return issueTokens(user)
    }

    @Transactional(readOnly = true)
    fun me(userId: java.util.UUID): AuthUserResponse {
        logger.info("[service] Fetching current user userId={}", userId)
        val user = userRepository.findById(userId).orElseThrow { UserNotFoundException() }
        user.ensureActive()
        return user.toAuthUserResponse()
    }

    @Transactional
    fun refresh(refreshToken: String?): AccessTokenResult {
        logger.info("[service] Refreshing access token hasToken={}", !refreshToken.isNullOrBlank())
        if (refreshToken.isNullOrBlank()) {
            throw UnauthorizedException("Refresh token is required")
        }

        val tokenHash = hashRefreshToken(refreshToken)
        val now = Instant.now()
        val session = refreshSessionRepository.findByTokenHashAndRevokedAtIsNull(tokenHash)
            ?: throw UnauthorizedException("Invalid refresh token")

        if (!session.isActive(now)) {
            session.revoke(now)
            refreshSessionRepository.save(session)
            throw UnauthorizedException("Refresh token expired")
        }

        val user = userRepository.findById(session.userId).orElseThrow { UnauthorizedException("User not found") }
        user.ensureActive()

        val accessToken = jwtService.generateAccessToken(user.toPrincipal())
        logger.info("[service] Access token refreshed userId={}", user.id)
        return AccessTokenResult(accessToken)
    }

    @Transactional
    fun logout(refreshToken: String?) {
        logger.info("[service] Logging out session hasToken={}", !refreshToken.isNullOrBlank())
        if (refreshToken.isNullOrBlank()) {
            return
        }

        val tokenHash = hashRefreshToken(refreshToken)
        val session = refreshSessionRepository.findByTokenHashAndRevokedAtIsNull(tokenHash) ?: return
        session.revoke(Instant.now())
        refreshSessionRepository.save(session)
        logger.info("[service] Session logged out userId={}", session.userId)
    }

    private fun issueTokens(user: User): AuthResult {
        val now = Instant.now()
        refreshSessionRepository.revokeActiveByUserId(user.id, now)

        val refreshToken = generateOpaqueToken()
        val refreshSession = RefreshSession(
            id = idGenerator.newId(),
            userId = user.id,
            tokenHash = hashRefreshToken(refreshToken),
            expiresAt = now.plusSeconds(refreshTokenDays * 24 * 60 * 60)
        )
        refreshSessionRepository.save(refreshSession)

        val accessToken = jwtService.generateAccessToken(user.toPrincipal())
        return AuthResult(
            user = user.toAuthUserResponse(),
            accessToken = accessToken,
            refreshToken = refreshToken
        )
    }

    private fun normalizeEmail(email: String): String = email.trim().lowercase()

    private fun hashRefreshToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(token.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(hash)
    }

    private fun generateOpaqueToken(): String {
        val bytes = ByteArray(48)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun User.toPrincipal(): AuthenticatedUser {
        return AuthenticatedUser(
            id = id,
            email = email,
            role = role
        )
    }
}
