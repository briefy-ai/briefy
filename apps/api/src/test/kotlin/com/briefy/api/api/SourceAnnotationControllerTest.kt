package com.briefy.api.api

import com.briefy.api.infrastructure.extraction.ExtractionProvider
import com.briefy.api.infrastructure.extraction.ExtractionProviderId
import com.briefy.api.infrastructure.extraction.ExtractionProviderResolver
import com.briefy.api.infrastructure.extraction.ExtractionResult
import com.briefy.api.infrastructure.security.CurrentUserProvider
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class SourceAnnotationControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var extractionProviderResolver: ExtractionProviderResolver

    @MockitoBean
    lateinit var currentUserProvider: CurrentUserProvider

    private val objectMapper: ObjectMapper = jacksonObjectMapper()
    private val testUserId: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val extractionProvider: ExtractionProvider = mock()

    private val sampleExtractionResult = ExtractionResult(
        text = "This is extracted content used for source annotation API tests.",
        title = "Annotation Test Article",
        author = "Briefy",
        publishedDate = Instant.parse("2024-01-15T10:00:00Z")
    )

    @BeforeEach
    fun setupCurrentUser() {
        `when`(currentUserProvider.requireUserId()).thenReturn(testUserId)
        `when`(extractionProviderResolver.resolveProvider(any(), any())).thenReturn(extractionProvider)
        `when`(extractionProvider.id).thenReturn(ExtractionProviderId.JSOUP)
        `when`(extractionProvider.extract(any())).thenReturn(sampleExtractionResult)
    }

    @Test
    fun `POST create annotation and GET list returns document-ordered annotations`() {
        val sourceId = createSource("https://annotation-list-test.com/article")

        val first = createAnnotation(
            sourceId = sourceId,
            body = "First",
            anchorQuote = "This",
            anchorStart = 0,
            anchorEnd = 4
        )

        val second = createAnnotation(
            sourceId = sourceId,
            body = "Second",
            anchorQuote = "annotation",
            anchorStart = 27,
            anchorEnd = 37
        )

        mockMvc.perform(get("/api/sources/$sourceId/annotations"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(first))
            .andExpect(jsonPath("$[1].id").value(second))
            .andExpect(jsonPath("$[0].status").value("active"))
            .andExpect(jsonPath("$[1].status").value("active"))
    }

    @Test
    fun `POST create annotation returns conflict for overlapping selection`() {
        val sourceId = createSource("https://annotation-overlap-test.com/article")

        createAnnotation(
            sourceId = sourceId,
            body = "Existing",
            anchorQuote = "is extracted",
            anchorStart = 5,
            anchorEnd = 17
        )

        mockMvc.perform(
            post("/api/sources/$sourceId/annotations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "body": "Overlap",
                      "anchorQuote": "s extracte",
                      "anchorPrefix": "",
                      "anchorSuffix": "",
                      "anchorStart": 6,
                      "anchorEnd": 16
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `PATCH updates annotation body`() {
        val sourceId = createSource("https://annotation-update-test.com/article")
        val annotationId = createAnnotation(
            sourceId = sourceId,
            body = "Before",
            anchorQuote = "This",
            anchorStart = 0,
            anchorEnd = 4
        )

        mockMvc.perform(
            patch("/api/sources/$sourceId/annotations/$annotationId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"body":"After"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(annotationId))
            .andExpect(jsonPath("$.body").value("After"))
    }

    @Test
    fun `DELETE archives annotation and hides it from active list`() {
        val sourceId = createSource("https://annotation-delete-test.com/article")
        val annotationId = createAnnotation(
            sourceId = sourceId,
            body = "To delete",
            anchorQuote = "This",
            anchorStart = 0,
            anchorEnd = 4
        )

        mockMvc.perform(delete("/api/sources/$sourceId/annotations/$annotationId"))
            .andExpect(status().isNoContent)

        mockMvc.perform(get("/api/sources/$sourceId/annotations"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[?(@.id=='$annotationId')]" ).isEmpty)
    }

    @Test
    fun `POST create annotation returns bad request for blank body`() {
        val sourceId = createSource("https://annotation-validation-test.com/article")

        mockMvc.perform(
            post("/api/sources/$sourceId/annotations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "body": "",
                      "anchorQuote": "This",
                      "anchorPrefix": "",
                      "anchorSuffix": "",
                      "anchorStart": 0,
                      "anchorEnd": 4
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isBadRequest)
    }

    private fun createSource(url: String): String {
        val result = mockMvc.perform(
            post("/api/sources")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"url":"$url"}""")
        )
            .andExpect(status().isCreated)
            .andReturn()

        return objectMapper.readTree(result.response.contentAsString).get("id").asText()
    }

    private fun createAnnotation(
        sourceId: String,
        body: String,
        anchorQuote: String,
        anchorStart: Int,
        anchorEnd: Int
    ): String {
        val result = mockMvc.perform(
            post("/api/sources/$sourceId/annotations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "body": "$body",
                      "anchorQuote": "$anchorQuote",
                      "anchorPrefix": "",
                      "anchorSuffix": "",
                      "anchorStart": $anchorStart,
                      "anchorEnd": $anchorEnd
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isCreated)
            .andReturn()

        return objectMapper.readTree(result.response.contentAsString).get("id").asText()
    }
}
