package com.briefy.api.infrastructure.security

import com.briefy.api.application.auth.UnauthorizedException
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CurrentUserProvider {
    fun requireUserId(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication
            ?: throw UnauthorizedException("Authentication required")
        return extractUserId(authentication)
    }

    private fun extractUserId(authentication: Authentication): UUID {
        val principal = authentication.principal

        if (principal is AuthenticatedUser) {
            return principal.id
        }

        if (principal is String && principal.isNotBlank()) {
            return parseUuid(principal)
        }

        if (authentication.name.isNotBlank()) {
            return parseUuid(authentication.name)
        }

        throw UnauthorizedException("Authentication required")
    }

    private fun parseUuid(raw: String): UUID {
        return try {
            UUID.fromString(raw)
        } catch (_: IllegalArgumentException) {
            throw UnauthorizedException("Invalid authenticated principal")
        }
    }
}
