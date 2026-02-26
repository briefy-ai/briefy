package com.briefy.api.api

import com.briefy.api.infrastructure.security.CurrentUserProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.ai.zhipuai.api-key=test-zhipu-key",
        "spring.ai.minimax.api-key=test-minimax-key",
        "spring.ai.google.genai.api-key=test-google-key"
    ]
)
class AiSettingsControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var currentUserProvider: CurrentUserProvider

    private val testUserId: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")

    @BeforeEach
    fun setupCurrentUser() {
        `when`(currentUserProvider.requireUserId()).thenReturn(testUserId)
    }

    @Test
    fun `GET ai settings includes providers and use cases`() {
        mockMvc.perform(get("/api/settings/ai"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.providers[?(@.id=='zhipuai')]").isNotEmpty)
            .andExpect(jsonPath("$.providers[?(@.id=='google_genai')]").isNotEmpty)
            .andExpect(jsonPath("$.providers[?(@.id=='minimax')]").isNotEmpty)
            .andExpect(jsonPath("$.useCases[0].id").value("topic_extraction"))
            .andExpect(jsonPath("$.useCases[1].id").value("source_formatting"))
    }

    @Test
    fun `PUT ai use case updates provider and model`() {
        mockMvc.perform(
            put("/api/settings/ai/use-cases/topic_extraction")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider":"google_genai","model":"gemini-2.5-flash"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.useCases[0].provider").value("google_genai"))
            .andExpect(jsonPath("$.useCases[0].model").value("gemini-2.5-flash"))
    }

    @Test
    fun `PUT ai use case rejects invalid model`() {
        mockMvc.perform(
            put("/api/settings/ai/use-cases/topic_extraction")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider":"google_genai","model":"glm-4.7"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `PUT ai use case updates to minimax provider and model`() {
        mockMvc.perform(
            put("/api/settings/ai/use-cases/source_formatting")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider":"minimax","model":"MiniMax-M2.5"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.useCases[1].provider").value("minimax"))
            .andExpect(jsonPath("$.useCases[1].model").value("MiniMax-M2.5"))
    }

    @Test
    fun `PUT ai use case rejects invalid minimax model`() {
        mockMvc.perform(
            put("/api/settings/ai/use-cases/source_formatting")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider":"minimax","model":"minimax/minimax-m2.5"}""")
        )
            .andExpect(status().isBadRequest)
    }
}
