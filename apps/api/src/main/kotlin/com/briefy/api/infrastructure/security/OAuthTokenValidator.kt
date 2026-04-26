package com.briefy.api.infrastructure.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

@Component
class OAuthTokenValidator(private val jwtService: JwtService) {

    fun validate(bearerToken: String): OAuthPrincipal? {
        val principal = jwtService.parseMcpAccessToken(bearerToken) ?: return null
        if (!principal.hasScope("mcp:read")) return null
        return principal
    }

    fun extractFromRequest(request: HttpServletRequest): OAuthPrincipal? {
        val authHeader = request.getHeader("Authorization") ?: return null
        if (!authHeader.startsWith("Bearer ")) return null
        return validate(authHeader.removePrefix("Bearer ").trim())
    }
}
