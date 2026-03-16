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
class ImageGenSettingsControllerTest {

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
    fun `GET image generation settings includes models`() {
        mockMvc.perform(get("/api/settings/image-gen"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.configured").value(false))
            .andExpect(jsonPath("$.selectedModel").value("google/gemini-3.1-flash-image-preview"))
            .andExpect(jsonPath("$.models[0].id").value("google/gemini-3.1-flash-image-preview"))
            .andExpect(jsonPath("$.models[1].id").value("bytedance-seed/seedream-4.5"))
            .andExpect(jsonPath("$.models[2].id").value("black-forest-labs/flux.2-max"))
    }

    @Test
    fun `PUT image generation settings updates enabled configured and model`() {
        mockMvc.perform(
            put("/api/settings/image-gen/provider")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"enabled":true,"apiKey":"or-key","modelId":"black-forest-labs/flux.2-max"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.configured").value(true))
            .andExpect(jsonPath("$.selectedModel").value("black-forest-labs/flux.2-max"))
    }

    @Test
    fun `DELETE image generation key disables provider`() {
        mockMvc.perform(
            put("/api/settings/image-gen/provider")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"enabled":true,"apiKey":"or-key","modelId":"google/gemini-3.1-flash-image-preview"}""")
        )
            .andExpect(status().isOk)

        mockMvc.perform(delete("/api/settings/image-gen/provider/key"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.configured").value(false))
    }
}
