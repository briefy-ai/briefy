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
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class TtsSettingsControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var currentUserProvider: CurrentUserProvider

    private lateinit var testUserId: UUID

    @BeforeEach
    fun setupCurrentUser() {
        testUserId = UUID.randomUUID()
        `when`(currentUserProvider.requireUserId()).thenReturn(testUserId)
    }

    @Test
    fun `GET tts settings includes elevenlabs and inworld`() {
        mockMvc.perform(get("/api/settings/tts"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.preferredProvider").value("elevenlabs"))
            .andExpect(jsonPath("$.providers[0].type").value("elevenlabs"))
            .andExpect(jsonPath("$.providers[1].type").value("inworld"))
    }

    @Test
    fun `PUT elevenlabs settings updates enabled configured and model`() {
        mockMvc.perform(
            put("/api/settings/tts/providers/elevenlabs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"enabled":true,"apiKey":"el-user-key","modelId":"eleven_turbo_v2_5"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.providers[0].enabled").value(true))
            .andExpect(jsonPath("$.providers[0].configured").value(true))
            .andExpect(jsonPath("$.providers[0].selectedModelId").value("eleven_turbo_v2_5"))
    }

    @Test
    fun `PUT inworld settings updates enabled configured and model`() {
        mockMvc.perform(
            put("/api/settings/tts/providers/inworld")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"enabled":true,"apiKey":"in-user-key","modelId":"inworld-tts-1.5-max"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.providers[1].enabled").value(true))
            .andExpect(jsonPath("$.providers[1].configured").value(true))
            .andExpect(jsonPath("$.providers[1].selectedModelId").value("inworld-tts-1.5-max"))
    }

    @Test
    fun `PUT preferred provider updates selected provider`() {
        mockMvc.perform(
            put("/api/settings/tts/providers/inworld")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"enabled":true,"apiKey":"in-user-key","modelId":"inworld-tts-1.5-max"}""")
        )
            .andExpect(status().isOk)

        mockMvc.perform(
            put("/api/settings/tts/preferred-provider")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"preferredProvider":"inworld"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.preferredProvider").value("inworld"))
    }

    @Test
    fun `PUT preferred provider rejects unconfigured provider`() {
        mockMvc.perform(
            put("/api/settings/tts/preferred-provider")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"preferredProvider":"inworld"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `DELETE inworld key disables provider`() {
        mockMvc.perform(
            put("/api/settings/tts/providers/inworld")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"enabled":true,"apiKey":"in-user-key","modelId":"inworld-tts-1.5-mini"}""")
        )
            .andExpect(status().isOk)

        mockMvc.perform(delete("/api/settings/tts/providers/inworld/key"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.providers[1].enabled").value(false))
            .andExpect(jsonPath("$.providers[1].configured").value(false))
    }
}
