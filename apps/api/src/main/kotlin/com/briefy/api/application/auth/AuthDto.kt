package com.briefy.api.application.auth

import com.briefy.api.domain.identity.user.User
import com.briefy.api.domain.identity.user.UserRole
import java.util.UUID

data class AuthUserResponse(
    val id: UUID,
    val email: String,
    val role: UserRole,
    val displayName: String?
)

data class AuthResult(
    val user: AuthUserResponse,
    val accessToken: String,
    val refreshToken: String
)

data class AccessTokenResult(
    val accessToken: String
)

fun User.toAuthUserResponse(): AuthUserResponse = AuthUserResponse(
    id = id,
    email = email,
    role = role,
    displayName = displayName
)
