package com.briefy.api.application.briefing

import com.briefy.api.domain.enrichment.AgentPersona
import com.briefy.api.domain.enrichment.AgentPersonaUseCase
import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.domain.knowledgegraph.source.Url
import com.briefy.api.infrastructure.ai.AiAdapter
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class AiBriefingPlanGeneratorTest {
    private val aiAdapter: AiAdapter = mock()
    private val objectMapper = ObjectMapper()

    private val generator = AiBriefingPlanGenerator(
        aiAdapter = aiAdapter,
        objectMapper = objectMapper,
        provider = "google_genai",
        model = "gemini-2.5-flash"
    )

    @Test
    fun `generate parses valid json response`() {
        val personas = listOf(createPersona("The Skeptic"), createPersona("The Scientist"))
        whenever(aiAdapter.complete(eq("google_genai"), eq("gemini-2.5-flash"), any(), any(), eq("briefing_planning")))
            .thenReturn(
                """
                {
                  "steps": [
                    {"personaName":"The Skeptic","task":"Challenge claims and identify assumptions from the source evidence."},
                    {"personaName":"The Scientist","task":"Evaluate evidence quality, methodology rigor, and uncertainty boundaries."}
                  ]
                }
                """.trimIndent()
            )

        val result = generator.generate(
            enrichmentIntent = "TRUTH_GROUNDING",
            sources = listOf(createSource()),
            personas = personas
        )

        assertEquals(2, result.size)
        assertEquals(personas[0].id, result[0].personaId)
        assertEquals("The Skeptic", result[0].personaName)
    }

    @Test
    fun `generate supports fenced json response`() {
        val personas = listOf(createPersona("The Skeptic"), createPersona("The Scientist"))
        whenever(aiAdapter.complete(eq("google_genai"), eq("gemini-2.5-flash"), any(), any(), eq("briefing_planning")))
            .thenReturn(
                """
                ```json
                {
                  "steps": [
                    {"personaName":"The Skeptic","task":"Challenge claims and identify assumptions from the source evidence."},
                    {"personaName":"The Scientist","task":"Evaluate evidence quality, methodology rigor, and uncertainty boundaries."}
                  ]
                }
                ```
                """.trimIndent()
            )

        val result = generator.generate(
            enrichmentIntent = "DEEP_DIVE",
            sources = listOf(createSource()),
            personas = personas
        )

        assertEquals(2, result.size)
    }

    @Test
    fun `generate throws when response has fewer than 2 valid steps`() {
        val personas = listOf(createPersona("The Skeptic"), createPersona("The Scientist"))
        whenever(aiAdapter.complete(eq("google_genai"), eq("gemini-2.5-flash"), any(), any(), eq("briefing_planning")))
            .thenReturn("""{"steps":[{"personaName":"The Skeptic","task":"Too short"}]}""")

        assertThrows(IllegalStateException::class.java) {
            generator.generate(
                enrichmentIntent = "DEEP_DIVE",
                sources = listOf(createSource()),
                personas = personas
            )
        }
    }

    private fun createPersona(name: String): AgentPersona {
        return AgentPersona(
            id = UUID.randomUUID(),
            userId = null,
            isSystem = true,
            useCase = AgentPersonaUseCase.ENRICHMENT,
            name = name,
            personality = "personality",
            role = "role",
            purpose = "purpose",
            description = "description",
            avatarUrl = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }

    private fun createSource(): Source {
        return Source(
            id = UUID.randomUUID(),
            url = Url.from("https://example.com/article"),
            status = SourceStatus.ACTIVE,
            content = Content.from("Long source content about the topic that the planner should inspect."),
            metadata = Metadata.from(
                title = "Example Title",
                author = "Author",
                publishedDate = Instant.now(),
                platform = "web",
                wordCount = 100,
                aiFormatted = true,
                extractionProvider = "jsoup"
            ),
            userId = UUID.randomUUID(),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
}
