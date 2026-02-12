package com.briefy.api.api

import com.briefy.api.infrastructure.extraction.ExtractionProvider
import com.briefy.api.infrastructure.extraction.ExtractionProviderId
import com.briefy.api.infrastructure.extraction.ExtractionProviderResolver
import com.briefy.api.infrastructure.extraction.ExtractionResult
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension::class)
class LoggingIntegrationTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var extractionProviderResolver: ExtractionProviderResolver

    private val objectMapper = jacksonObjectMapper()
    private val extractionProvider: ExtractionProvider = mock()

    @Test
    fun `logs include trace id and user id for authenticated request`(output: CapturedOutput) {
        whenever(extractionProviderResolver.resolveProvider(any(), any())).thenReturn(extractionProvider)
        whenever(extractionProvider.id).thenReturn(ExtractionProviderId.JSOUP)
        whenever(extractionProvider.extract(any())).thenReturn(
            ExtractionResult(
                text = "This is test content with enough words for metadata",
                title = "Test title",
                author = "Test author",
                publishedDate = Instant.parse("2026-01-01T00:00:00Z")
            )
        )

        val auth = signUp("logs-user@example.com")
        val traceId = "0123456789abcdef0123456789abcdef"

        mockMvc.perform(
            post("/api/sources")
                .cookie(auth.accessCookie)
                .header("traceparent", "00-$traceId-0123456789abcdef-01")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url":"https://example.com/logging-test"}""")
        )
            .andExpect(status().isCreated)

        val logs = output.out
        assertTrue(logs.contains("Successfully extracted content"), "Expected service success log")
        assertTrue(logs.contains("\"traceId\":\"$traceId\""), "Expected traceId in logs")
        assertTrue(logs.contains("\"userId\":\"${auth.userId}\""), "Expected userId in logs")
        assertTrue(
            logs.contains("\"logger_name\":\"com.briefy.api.application.source.SourceService\""),
            "Expected logger name in logs"
        )
    }

    private fun signUp(email: String): AuthSession {
        val result = mockMvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "email":"$email",
                      "password":"password123",
                      "displayName":"Logs User"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andReturn()

        val responseBody = objectMapper.readTree(result.response.contentAsString)
        val userId = responseBody.get("id").asText()
        val accessCookie = parseCookie(result.response.getHeaders("Set-Cookie"), "briefy_access_token")
        return AuthSession(userId, accessCookie)
    }

    private fun parseCookie(headers: List<String>, cookieName: String): Cookie {
        val raw = headers.firstOrNull { it.startsWith("$cookieName=") }
            ?: error("Missing cookie $cookieName")
        val value = raw.substringAfter("$cookieName=").substringBefore(";")
        return Cookie(cookieName, value)
    }

    private data class AuthSession(
        val userId: String,
        val accessCookie: Cookie
    )
}
