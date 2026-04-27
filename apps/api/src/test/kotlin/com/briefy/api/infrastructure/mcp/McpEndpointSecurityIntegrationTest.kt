package com.briefy.api.infrastructure.mcp

import com.briefy.api.infrastructure.security.JwtService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class McpEndpointSecurityIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var jwtService: JwtService

    @Test
    fun `mcp sse rejects requests without bearer token`() {
        mockMvc.perform(get("/mcp/sse"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `mcp sse rejects bogus bearer token`() {
        mockMvc.perform(get("/mcp/sse").header("Authorization", "Bearer not-a-real-jwt"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `mcp sse rejects token without mcp read scope`() {
        val token = jwtService.generateMcpAccessToken(
            userId = UUID.randomUUID(),
            scopes = listOf("other:scope"),
            clientId = "espriu"
        )
        mockMvc.perform(get("/mcp/sse").header("Authorization", "Bearer $token"))
            .andExpect(status().isUnauthorized)
    }
}
