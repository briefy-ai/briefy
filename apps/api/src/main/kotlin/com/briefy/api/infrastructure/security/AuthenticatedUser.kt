package com.briefy.api.infrastructure.security

import com.briefy.api.domain.identity.user.UserRole
import java.util.UUID

data class AuthenticatedUser(
    val id: UUID,
    val email: String,
    val role: UserRole
)
