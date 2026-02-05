package com.briefy.api.api

import com.briefy.api.infrastructure.extraction.ContentExtractor
import com.briefy.api.infrastructure.extraction.ExtractionResult
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.Instant

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SourceControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var contentExtractor: ContentExtractor

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    private val sampleExtractionResult = ExtractionResult(
        text = "This is the extracted article content with enough words to test",
        title = "Test Article Title",
        author = "Test Author",
        publishedDate = Instant.parse("2024-01-15T10:00:00Z")
    )

    @Test
    fun `POST creates source and extracts content`() {
        `when`(contentExtractor.extract(any())).thenReturn(sampleExtractionResult)

        mockMvc.perform(
            post("/api/sources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url": "https://example.com/article"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.url.normalized").value("https://example.com/article"))
            .andExpect(jsonPath("$.url.platform").value("web"))
            .andExpect(jsonPath("$.status").value("active"))
            .andExpect(jsonPath("$.content.text").value(sampleExtractionResult.text))
            .andExpect(jsonPath("$.content.wordCount").isNumber)
            .andExpect(jsonPath("$.metadata.title").value("Test Article Title"))
            .andExpect(jsonPath("$.metadata.author").value("Test Author"))
            .andExpect(jsonPath("$.metadata.estimatedReadingTime").isNumber)
            .andExpect(jsonPath("$.id").isString)
            .andExpect(jsonPath("$.createdAt").isString)
    }

    @Test
    fun `POST returns conflict for duplicate URL`() {
        `when`(contentExtractor.extract(any())).thenReturn(sampleExtractionResult)

        // Create first
        mockMvc.perform(
            post("/api/sources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url": "https://duplicate-test.com/article"}""")
        )
            .andExpect(status().isCreated)

        // Try duplicate
        mockMvc.perform(
            post("/api/sources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url": "https://duplicate-test.com/article"}""")
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `POST returns bad request for blank url`() {
        mockMvc.perform(
            post("/api/sources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url": ""}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `POST returns 422 when extraction fails`() {
        `when`(contentExtractor.extract(any())).thenReturn(sampleExtractionResult)
            .thenThrow(RuntimeException("Connection refused"))

        // First call to create a unique URL that will fail on extraction
        `when`(contentExtractor.extract(any())).thenThrow(RuntimeException("Connection refused"))

        mockMvc.perform(
            post("/api/sources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url": "https://unreachable-test.com"}""")
        )
            .andExpect(status().isUnprocessableEntity)
    }

    @Test
    fun `GET list returns all sources`() {
        `when`(contentExtractor.extract(any())).thenReturn(sampleExtractionResult)

        // Create a source
        mockMvc.perform(
            post("/api/sources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url": "https://list-test.com/article"}""")
        )
            .andExpect(status().isCreated)

        // List all
        mockMvc.perform(get("/api/sources"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
    }

    @Test
    fun `GET list filters by status`() {
        mockMvc.perform(get("/api/sources").param("status", "active"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
    }

    @Test
    fun `GET by id returns source`() {
        `when`(contentExtractor.extract(any())).thenReturn(sampleExtractionResult)

        // Create a source
        val result = mockMvc.perform(
            post("/api/sources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url": "https://get-test.com/article"}""")
        )
            .andExpect(status().isCreated)
            .andReturn()

        val id = objectMapper.readTree(result.response.contentAsString).get("id").asText()

        // Get by id
        mockMvc.perform(get("/api/sources/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.status").value("active"))
            .andExpect(jsonPath("$.content.text").exists())
    }

    @Test
    fun `GET by id returns 404 for unknown id`() {
        mockMvc.perform(get("/api/sources/00000000-0000-0000-0000-000000000000"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST retry returns 404 for unknown id`() {
        mockMvc.perform(post("/api/sources/00000000-0000-0000-0000-000000000000/retry"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `POST retry returns bad request for non-failed source`() {
        `when`(contentExtractor.extract(any())).thenReturn(sampleExtractionResult)

        // Create a source (will be ACTIVE)
        val result = mockMvc.perform(
            post("/api/sources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url": "https://retry-test.com/article"}""")
        )
            .andExpect(status().isCreated)
            .andReturn()

        val id = objectMapper.readTree(result.response.contentAsString).get("id").asText()

        // Try to retry an active source
        mockMvc.perform(post("/api/sources/$id/retry"))
            .andExpect(status().isBadRequest)
    }
}
