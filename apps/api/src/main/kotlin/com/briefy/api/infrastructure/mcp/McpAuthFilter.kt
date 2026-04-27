package com.briefy.api.infrastructure.mcp

import com.briefy.api.api.ErrorResponse
import com.briefy.api.infrastructure.security.OAuthTokenValidator
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant

@Component
class McpAuthFilter(
    private val tokenValidator: OAuthTokenValidator,
    private val objectMapper: ObjectMapper,
    @Value("\${oauth.server.base-url:http://localhost:8080}") private val authServerBaseUrl: String,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val principal = tokenValidator.extractFromRequest(request)
        if (principal == null) {
            writeUnauthorized(response)
            return
        }

        val authorities = principal.scopes.map { SimpleGrantedAuthority("SCOPE_$it") }
        val authentication = UsernamePasswordAuthenticationToken(principal, null, authorities)
        SecurityContextHolder.getContext().authentication = authentication

        filterChain.doFilter(request, response)
    }

    private fun writeUnauthorized(response: HttpServletResponse) {
        response.status = HttpStatus.UNAUTHORIZED.value()
        val resourceMetadataUrl = "$authServerBaseUrl/.well-known/oauth-protected-resource"
        response.setHeader(
            "WWW-Authenticate",
            "Bearer realm=\"briefy-mcp\", error=\"invalid_token\", resource_metadata=\"$resourceMetadataUrl\""
        )
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        val payload = ErrorResponse(
            status = HttpStatus.UNAUTHORIZED.value(),
            error = HttpStatus.UNAUTHORIZED.reasonPhrase,
            message = "Valid mcp:read access token required",
            timestamp = Instant.now()
        )
        response.writer.write(objectMapper.writeValueAsString(payload))
    }
}
