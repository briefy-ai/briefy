package com.briefy.api.application.briefing

import com.briefy.api.domain.enrichment.AgentPersona
import com.briefy.api.domain.enrichment.AgentPersonaRepository
import com.briefy.api.domain.enrichment.AgentPersonaUseCase
import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.domain.knowledgegraph.source.Url
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class BriefingPlannerServiceTest {
    private val agentPersonaRepository: AgentPersonaRepository = mock()
    private val aiBriefingPlanGenerator: AiBriefingPlanGenerator = mock()
    private val deterministicBriefingPlanGenerator: DeterministicBriefingPlanGenerator = mock()

    @Test
    fun `buildPlan returns deterministic fallback when no personas exist`() {
        val service = createService(aiPlanningEnabled = true)
        val userId = UUID.randomUUID()
        whenever(agentPersonaRepository.findForUseCase(userId, AgentPersonaUseCase.ENRICHMENT)).thenReturn(emptyList())
        whenever(deterministicBriefingPlanGenerator.generate(any(), any(), any())).thenReturn(
            listOf(BriefingPlanDraft(null, "Claim Auditor", "Fallback task"))
        )

        val plan = service.buildPlan(userId, "TRUTH_GROUNDING", listOf(createActiveSource(userId, "Source A")))

        assertEquals(1, plan.size)
        assertEquals("Claim Auditor", plan[0].personaName)
        verify(aiBriefingPlanGenerator, never()).generate(any(), any(), any())
    }

    @Test
    fun `buildPlan uses ai generator when enabled and personas exist`() {
        val service = createService(aiPlanningEnabled = true)
        val userId = UUID.randomUUID()
        val personas = listOf(
            createPersona("The Skeptic"),
            createPersona("The Scientist"),
            createPersona("The Economist")
        )
        whenever(agentPersonaRepository.findForUseCase(userId, AgentPersonaUseCase.ENRICHMENT)).thenReturn(personas)
        whenever(aiBriefingPlanGenerator.generate(any(), any(), any())).thenReturn(
            listOf(
                BriefingPlanDraft(personas[0].id, personas[0].name, "Challenge assumptions and extract claims."),
                BriefingPlanDraft(personas[1].id, personas[1].name, "Evaluate evidence quality and methods.")
            )
        )

        val plan = service.buildPlan(
            userId,
            "DEEP_DIVE",
            listOf(createActiveSource(userId, "Architecture Notes"))
        )

        assertEquals(2, plan.size)
        assertEquals("The Skeptic", plan[0].personaName)
        verify(deterministicBriefingPlanGenerator, never()).generate(any(), any(), any())
    }

    @Test
    fun `buildPlan falls back to deterministic when ai generation fails`() {
        val service = createService(aiPlanningEnabled = true)
        val userId = UUID.randomUUID()
        val personas = listOf(createPersona("The Skeptic"))
        whenever(agentPersonaRepository.findForUseCase(userId, AgentPersonaUseCase.ENRICHMENT)).thenReturn(personas)
        whenever(aiBriefingPlanGenerator.generate(any(), any(), any())).thenThrow(IllegalStateException("invalid plan"))
        whenever(deterministicBriefingPlanGenerator.generate(any(), any(), any())).thenReturn(
            listOf(BriefingPlanDraft(personas[0].id, personas[0].name, "Deterministic fallback task"))
        )

        val plan = service.buildPlan(userId, "DEEP_DIVE", listOf(createActiveSource(userId, "Architecture Notes")))

        assertEquals(1, plan.size)
        assertEquals("The Skeptic", plan[0].personaName)
        verify(deterministicBriefingPlanGenerator).generate(any(), any(), any())
    }

    @Test
    fun `buildPlan uses deterministic generator when ai planning is disabled`() {
        val service = createService(aiPlanningEnabled = false)
        val userId = UUID.randomUUID()
        val personas = listOf(createPersona("The Skeptic"))
        whenever(agentPersonaRepository.findForUseCase(userId, AgentPersonaUseCase.ENRICHMENT)).thenReturn(personas)
        whenever(deterministicBriefingPlanGenerator.generate(any(), any(), any())).thenReturn(
            listOf(BriefingPlanDraft(personas[0].id, personas[0].name, "Deterministic task"))
        )

        val plan = service.buildPlan(userId, "DEEP_DIVE", listOf(createActiveSource(userId, "Architecture Notes")))

        assertEquals(1, plan.size)
        verify(aiBriefingPlanGenerator, never()).generate(any(), any(), any())
    }

    private fun createService(aiPlanningEnabled: Boolean): BriefingPlannerService {
        return BriefingPlannerService(
            agentPersonaRepository = agentPersonaRepository,
            aiBriefingPlanGenerator = aiBriefingPlanGenerator,
            deterministicBriefingPlanGenerator = deterministicBriefingPlanGenerator,
            aiPlanningEnabled = aiPlanningEnabled
        )
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

    private fun createActiveSource(userId: UUID, title: String): Source {
        val slug = title.lowercase().replace(' ', '-')
        return Source(
            id = UUID.randomUUID(),
            url = Url.from("https://example.com/$slug"),
            status = SourceStatus.ACTIVE,
            content = Content.from("Sample content for $title"),
            metadata = Metadata.from(
                title = title,
                author = "author",
                publishedDate = Instant.now(),
                platform = "web",
                wordCount = 20,
                aiFormatted = true,
                extractionProvider = "jsoup"
            ),
            userId = userId,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
}
