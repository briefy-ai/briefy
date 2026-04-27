package com.briefy.api.infrastructure.mcp

import com.briefy.api.infrastructure.security.OAuthPrincipal
import com.briefy.api.infrastructure.security.OAuthTokenValidator
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

class McpAuthFilterTest {

    private val tokenValidator: OAuthTokenValidator = mock()
    private val objectMapper = ObjectMapper().findAndRegisterModules()
    private val filter = McpAuthFilter(tokenValidator, objectMapper, "http://localhost:8081")

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `valid bearer token populates security context with OAuthPrincipal`() {
        val userId = UUID.randomUUID()
        val principal = OAuthPrincipal(userId, listOf("mcp:read"))
        val request = MockHttpServletRequest("GET", "/mcp/sse").apply {
            addHeader("Authorization", "Bearer valid-token")
        }
        val response = MockHttpServletResponse()
        val chain: FilterChain = mock()
        whenever(tokenValidator.extractFromRequest(any())).thenReturn(principal)

        filter.doFilter(request, response, chain)

        verify(chain).doFilter(eq(request), eq(response))
        val auth = SecurityContextHolder.getContext().authentication
        assertNotNull(auth)
        assertEquals(principal, auth!!.principal)
        assertEquals(200, response.status)
    }

    @Test
    fun `missing Authorization header returns 401 and short circuits chain`() {
        val request = MockHttpServletRequest("GET", "/mcp/sse")
        val response = MockHttpServletResponse()
        val chain: FilterChain = mock()
        whenever(tokenValidator.extractFromRequest(any())).thenReturn(null)

        filter.doFilter(request, response, chain)

        verify(chain, never()).doFilter(any<HttpServletRequest>(), any<HttpServletResponse>())
        assertEquals(401, response.status)
        assertNotNull(response.getHeader("WWW-Authenticate"))
        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `invalid token returns 401 with error body`() {
        val request = MockHttpServletRequest("GET", "/mcp/sse").apply {
            addHeader("Authorization", "Bearer bogus")
        }
        val response = MockHttpServletResponse()
        val chain: FilterChain = mock()
        whenever(tokenValidator.extractFromRequest(any())).thenReturn(null)

        filter.doFilter(request, response, chain)

        verify(chain, never()).doFilter(any<HttpServletRequest>(), any<HttpServletResponse>())
        assertEquals(401, response.status)
        val body = response.contentAsString
        assert(body.contains("mcp:read"))
    }
}
