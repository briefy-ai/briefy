package com.briefy.api.infrastructure.logging

import com.briefy.api.domain.identity.user.UserRole
import com.briefy.api.infrastructure.security.AuthenticatedUser
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

class RequestMdcFilterTest {

    private val filter = RequestMdcFilter()

    @AfterEach
    fun cleanup() {
        SecurityContextHolder.clearContext()
        MDC.clear()
    }

    @Test
    fun `sets authenticated user id and request metadata in mdc`() {
        val userId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val principal = AuthenticatedUser(userId, "user@example.com", UserRole.USER)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, emptyList())

        val request = MockHttpServletRequest("POST", "/api/sources")
        request.addHeader("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, assertingChain { _, _ ->
            assertEquals(userId.toString(), MDC.get("userId"))
            assertEquals("POST", MDC.get("httpMethod"))
            assertEquals("/api/sources", MDC.get("path"))
            assertEquals("0123456789abcdef0123456789abcdef", MDC.get("traceId"))
            assertEquals("0123456789abcdef", MDC.get("spanId"))
        })

        assertNull(MDC.get("userId"))
        assertNull(MDC.get("httpMethod"))
        assertNull(MDC.get("path"))
        assertNull(MDC.get("traceId"))
        assertNull(MDC.get("spanId"))
    }

    @Test
    fun `sets anonymous user id when no authentication exists`() {
        val request = MockHttpServletRequest("GET", "/api/health")
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, assertingChain { _, _ ->
            assertEquals("anonymous", MDC.get("userId"))
            assertEquals("GET", MDC.get("httpMethod"))
            assertEquals("/api/health", MDC.get("path"))
            assertEquals(32, MDC.get("traceId")?.length)
            assertEquals(16, MDC.get("spanId")?.length)
        })

        assertNull(MDC.get("userId"))
        assertNull(MDC.get("httpMethod"))
        assertNull(MDC.get("path"))
        assertNull(MDC.get("traceId"))
        assertNull(MDC.get("spanId"))
    }

    private fun assertingChain(
        assertion: (HttpServletRequest, HttpServletResponse) -> Unit
    ): FilterChain {
        return FilterChain { request, response ->
            assertion(request as HttpServletRequest, response as HttpServletResponse)
        }
    }
}
