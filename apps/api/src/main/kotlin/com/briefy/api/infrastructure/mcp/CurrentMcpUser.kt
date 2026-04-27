package com.briefy.api.infrastructure.mcp

import com.briefy.api.infrastructure.security.OAuthPrincipal
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

object CurrentMcpUser {
    fun userId(): UUID {
        val auth = SecurityContextHolder.getContext().authentication
            ?: throw AccessDeniedException("No authenticated MCP principal")
        val principal = auth.principal as? OAuthPrincipal
            ?: throw AccessDeniedException("Principal is not an OAuthPrincipal")
        return principal.userId
    }
}
