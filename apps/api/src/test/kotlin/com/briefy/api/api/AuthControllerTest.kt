package com.briefy.api.api

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `signup sets cookies and returns created user`() {
        val result = mockMvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email":"user1@example.com",
                      "password":"password123",
                      "displayName":"User One"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.email").value("user1@example.com"))
            .andExpect(jsonPath("$.role").value("USER"))
            .andReturn()

        val cookies = result.response.getHeaders("Set-Cookie")
        assertNotNull(cookies.firstOrNull { it.startsWith("briefy_access_token=") })
        assertNotNull(cookies.firstOrNull { it.startsWith("briefy_refresh_token=") })
    }

    @Test
    fun `login returns unauthorized for invalid credentials`() {
        mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email":"missing@example.com",
                      "password":"password123"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `me returns 401 when unauthenticated`() {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `refresh succeeds with valid refresh token and fails after logout`() {
        val signUp = signUp("user2@example.com")
        val refreshCookie = signUp.refreshCookie

        mockMvc.perform(
            post("/api/auth/refresh")
                .cookie(refreshCookie)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ok").value(true))

        mockMvc.perform(
            post("/api/auth/logout")
                .cookie(refreshCookie)
        )
            .andExpect(status().isNoContent)

        mockMvc.perform(
            post("/api/auth/refresh")
                .cookie(refreshCookie)
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `protected routes return 401 without auth`() {
        mockMvc.perform(get("/api/sources"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `me returns current user with access token cookie`() {
        val signUp = signUp("user3@example.com")
        mockMvc.perform(
            get("/api/auth/me")
                .cookie(signUp.accessCookie)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("user3@example.com"))
            .andExpect(jsonPath("$.role").value("USER"))
    }

    private fun signUp(email: String): AuthCookies {
        val result = mockMvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email":"$email",
                      "password":"password123",
                      "displayName":"Auth Test"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andReturn()

        val cookies = result.response.getHeaders("Set-Cookie")
        val accessCookie = parseCookie(cookies, "briefy_access_token")
        val refreshCookie = parseCookie(cookies, "briefy_refresh_token")

        return AuthCookies(accessCookie, refreshCookie)
    }

    private fun parseCookie(headers: List<String>, cookieName: String): Cookie {
        val raw = headers.firstOrNull { it.startsWith("$cookieName=") }
            ?: error("Missing cookie $cookieName")
        val cookieValue = raw.substringAfter("$cookieName=").substringBefore(";")
        return Cookie(cookieName, cookieValue)
    }

    private data class AuthCookies(
        val accessCookie: Cookie,
        val refreshCookie: Cookie
    )
}
