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
class SettingsControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var currentUserProvider: CurrentUserProvider

    private val testUserId: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")

    @BeforeEach
    fun setupCurrentUser() {
        `when`(currentUserProvider.requireUserId()).thenReturn(testUserId)
    }

    @Test
    fun `GET extraction settings includes firecrawl and jsoup without secrets`() {
        mockMvc.perform(get("/api/settings/extraction"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.providers[0].type").value("firecrawl"))
            .andExpect(jsonPath("$.providers[1].type").value("jsoup"))
    }

    @Test
    fun `PUT firecrawl updates enabled and configured state`() {
        mockMvc.perform(
            put("/api/settings/extraction/providers/firecrawl")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"enabled":true,"apiKey":"fc-user-key"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.providers[0].enabled").value(true))
            .andExpect(jsonPath("$.providers[0].configured").value(true))
    }

    @Test
    fun `DELETE firecrawl key disables provider`() {
        mockMvc.perform(
            put("/api/settings/extraction/providers/firecrawl")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"enabled":true,"apiKey":"fc-user-key"}""")
        )
            .andExpect(status().isOk)

        mockMvc.perform(delete("/api/settings/extraction/providers/firecrawl/key"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.providers[0].enabled").value(false))
            .andExpect(jsonPath("$.providers[0].configured").value(false))
    }
}
