package com.briefy.api.infrastructure.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import java.util.UUID

class OAuthTokenValidatorTest {

    private val jwtService = JwtService(
        secret = "test-secret-should-be-at-least-32-bytes-long",
        accessTokenMinutes = 15
    )
    private val validator = OAuthTokenValidator(jwtService)

    @Test
    fun `extracts bearer token case-insensitively`() {
        val userId = UUID.randomUUID()
        val token = jwtService.generateMcpAccessToken(userId, listOf("mcp:read"), "espriu")
        val request = MockHttpServletRequest().apply {
            addHeader("Authorization", "bearer $token")
        }

        val principal = validator.extractFromRequest(request)

        assertNotNull(principal)
        assertEquals(userId, principal!!.userId)
    }
}
