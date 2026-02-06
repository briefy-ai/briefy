package com.briefy.api.domain.identity.user

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class User(
    @Id
    val id: UUID,

    @Column(name = "email", nullable = false, length = 320, unique = true)
    val email: String,

    @Column(name = "password_hash", nullable = false, length = 255)
    var passwordHash: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    var role: UserRole = UserRole.USER,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: UserStatus = UserStatus.ACTIVE,

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    val authProvider: AuthProvider = AuthProvider.LOCAL,

    @Column(name = "provider_subject", length = 255)
    val providerSubject: String? = null,

    @Column(name = "display_name", length = 120)
    var displayName: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    fun ensureActive() {
        if (status != UserStatus.ACTIVE) {
            throw IllegalStateException("User account is disabled")
        }
    }

    companion object {
        fun createLocal(id: UUID, email: String, passwordHash: String, displayName: String?): User {
            return User(
                id = id,
                email = email,
                passwordHash = passwordHash,
                role = UserRole.USER,
                status = UserStatus.ACTIVE,
                authProvider = AuthProvider.LOCAL,
                displayName = displayName?.trim()?.takeIf { it.isNotBlank() }?.take(120)
            )
        }
    }
}
