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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class BriefingPlannerServiceTest {
    private val agentPersonaRepository: AgentPersonaRepository = mock()
    private val service = BriefingPlannerService(agentPersonaRepository)

    @Test
    fun `buildPlan returns fallback steps when no personas exist`() {
        val userId = UUID.randomUUID()
        whenever(agentPersonaRepository.findForUseCase(userId, AgentPersonaUseCase.ENRICHMENT)).thenReturn(emptyList())

        val plan = service.buildPlan(userId, "TRUTH_GROUNDING", listOf(createActiveSource(userId, "Source A")))

        assertEquals(3, plan.size)
        assertEquals("Claim Auditor", plan[0].personaName)
        assertTrue(plan.all { it.personaId == null })
    }

    @Test
    fun `buildPlan uses persisted personas when available`() {
        val userId = UUID.randomUUID()
        val personas = listOf(
            createPersona("The Skeptic"),
            createPersona("The Scientist"),
            createPersona("The Economist")
        )
        whenever(agentPersonaRepository.findForUseCase(userId, AgentPersonaUseCase.ENRICHMENT)).thenReturn(personas)

        val plan = service.buildPlan(
            userId,
            "DEEP_DIVE",
            listOf(createActiveSource(userId, "Architecture Notes"))
        )

        assertEquals(3, plan.size)
        assertEquals("The Skeptic", plan[0].personaName)
        assertTrue(plan[0].task.contains("Step 1"))
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
