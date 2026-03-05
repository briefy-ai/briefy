package com.briefy.api.application.briefing

import com.briefy.api.infrastructure.ai.AiAdapter
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class AiSynthesisExecutionRunnerTest {

    private val aiAdapter = mock<AiAdapter>()
    private val objectMapper = ObjectMapper()
    private val runner = AiSynthesisExecutionRunner(
        aiAdapter = aiAdapter,
        objectMapper = objectMapper,
        config = AiSynthesisExecutionRunner.AiRunnerConfig(
            provider = "google_genai",
            model = "gemini-2.5-flash"
        )
    )

    @Test
    fun `returns structured synthesis and merges persona references`() {
        whenever(aiAdapter.complete(any(), any(), any(), any(), any())).thenReturn(
            """```json
{
  "markdownBody": "## Final Briefing\\n\\nConsensus with explicit disagreements.",
  "references": [
    {
      "url": "https://external.example.com/new",
      "title": "New external source",
      "snippet": "additional context"
    },
    {
      "url": "https://persona.example.com/ref-a",
      "title": "Duplicate from persona",
      "snippet": null
    }
  ],
  "conflictHighlights": [
    {
      "claim": "AI impact is immediate.",
      "counterClaim": "Adoption will be slower in regulated sectors.",
      "confidence": 0.82,
      "evidenceCitationLabels": ["[1]", "[3]"]
    }
  ]
}
```"""
        )

        val result = runner.run(baseRequest())

        assertTrue(result.markdownBody.contains("Final Briefing"))
        assertEquals(2, result.references.size)
        assertTrue(result.references.any { it.url == "https://persona.example.com/ref-a" })
        assertTrue(result.references.any { it.url == "https://external.example.com/new" })
        assertEquals(1, result.conflictHighlights.size)
        assertEquals(0.82, result.conflictHighlights.first().confidence)
    }

    @Test
    fun `falls back to raw markdown and preserves persona references when JSON is missing`() {
        whenever(aiAdapter.complete(any(), any(), any(), any(), any())).thenReturn(
            "## Synthesized Briefing\\n\\nPlain markdown response without JSON wrapper."
        )

        val result = runner.run(baseRequest())

        assertTrue(result.markdownBody.contains("Synthesized Briefing"))
        assertEquals(1, result.references.size)
        assertEquals("https://persona.example.com/ref-a", result.references.first().url)
        assertTrue(result.conflictHighlights.isEmpty())
    }

    @Test
    fun `blank AI response uses deterministic fallback markdown`() {
        whenever(aiAdapter.complete(any(), any(), any(), any(), any())).thenReturn("   ")

        val result = runner.run(baseRequest())

        assertTrue(result.markdownBody.contains("## Briefing"))
        assertTrue(result.markdownBody.contains("Persona One"))
        assertEquals(1, result.references.size)
    }

    @Test
    fun `non-text markdownBody in JSON triggers deterministic fallback`() {
        whenever(aiAdapter.complete(any(), any(), any(), any(), any())).thenReturn(
            """{
  "markdownBody": null,
  "references": [],
  "conflictHighlights": []
}"""
        )

        val result = runner.run(baseRequest())

        assertTrue(result.markdownBody.contains("## Briefing"))
        assertTrue(result.markdownBody.contains("Persona One"))
        assertEquals(1, result.references.size)
    }

    private fun baseRequest(): BriefingGenerationRequest {
        return BriefingGenerationRequest(
            briefingId = UUID.randomUUID(),
            userId = UUID.randomUUID(),
            enrichmentIntent = "DEEP_DIVE",
            sources = listOf(
                BriefingSourceInput(
                    sourceId = UUID.randomUUID(),
                    title = "Source One",
                    url = "https://source.example.com/1",
                    text = "Source one text"
                )
            ),
            plan = listOf(
                BriefingPlanInput(
                    personaName = "Persona One",
                    task = "Assess near-term impact",
                    stepOrder = 1
                )
            ),
            subagentOutputs = listOf(
                BriefingSubagentOutputInput(
                    personaKey = "step-1",
                    personaName = "Persona One",
                    task = "Assess near-term impact",
                    curatedText = "Persona output with evidence.",
                    references = listOf(
                        BriefingReferenceCandidate(
                            url = "https://persona.example.com/ref-a",
                            title = "Persona reference",
                            snippet = "Key supporting snippet"
                        )
                    )
                )
            )
        )
    }
}
