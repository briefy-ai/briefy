package com.briefy.api.infrastructure.security

import com.briefy.api.domain.identity.user.UserRole
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.UUID

class JwtServiceTest {

    private val jwtService = JwtService(
        secret = "test-secret-should-be-at-least-32-bytes-long",
        accessTokenMinutes = 15
    )

    @Test
    fun `generate and parse access token`() {
        val user = AuthenticatedUser(
            id = UUID.randomUUID(),
            email = "jwt@example.com",
            role = UserRole.USER
        )

        val token = jwtService.generateAccessToken(user)
        val parsed = jwtService.parseAccessToken(token)

        assertNotNull(parsed)
        assertEquals(user.id, parsed!!.id)
        assertEquals(user.email, parsed.email)
        assertEquals(user.role, parsed.role)
    }
}
