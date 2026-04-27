package com.briefy.api.infrastructure.mcp

import com.briefy.api.infrastructure.security.OAuthPrincipal
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

class CurrentMcpUserTest {

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `returns userId from OAuthPrincipal in security context`() {
        val userId = UUID.randomUUID()
        val principal = OAuthPrincipal(userId, listOf("mcp:read"))
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, emptyList())

        assertEquals(userId, CurrentMcpUser.userId())
    }

    @Test
    fun `throws when no authentication is present`() {
        SecurityContextHolder.clearContext()
        assertThrows(AccessDeniedException::class.java) { CurrentMcpUser.userId() }
    }

    @Test
    fun `throws when principal is not an OAuthPrincipal`() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken("not-a-principal", null, emptyList())

        assertThrows(AccessDeniedException::class.java) { CurrentMcpUser.userId() }
    }
}
