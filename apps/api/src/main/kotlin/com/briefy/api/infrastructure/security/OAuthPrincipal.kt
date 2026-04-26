package com.briefy.api.infrastructure.security

import java.util.UUID

data class OAuthPrincipal(
    val userId: UUID,
    val scopes: List<String>
) {
    fun hasScope(scope: String): Boolean = scopes.contains(scope)
}
