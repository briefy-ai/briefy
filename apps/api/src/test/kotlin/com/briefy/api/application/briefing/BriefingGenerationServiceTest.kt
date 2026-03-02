package com.briefy.api.application.briefing

import com.briefy.api.domain.knowledgegraph.briefing.*
import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.domain.knowledgegraph.source.Url
import com.briefy.api.infrastructure.id.IdGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class BriefingGenerationServiceTest {
    private val briefingRepository: BriefingRepository = mock()
    private val briefingSourceRepository: BriefingSourceRepository = mock()
    private val briefingPlanStepRepository: BriefingPlanStepRepository = mock()
    private val briefingReferenceRepository: BriefingReferenceRepository = mock()
    private val sourceRepository: SourceRepository = mock()
    private val briefingGenerationEngine: BriefingGenerationEngine = mock()
    private val briefingExecutionOrchestratorService: BriefingExecutionOrchestratorService = mock()
    private val idGenerator: IdGenerator = mock()
    private val objectMapper = ObjectMapper()

    private val service = BriefingGenerationService(
        briefingRepository = briefingRepository,
        briefingSourceRepository = briefingSourceRepository,
        briefingPlanStepRepository = briefingPlanStepRepository,
        briefingReferenceRepository = briefingReferenceRepository,
        sourceRepository = sourceRepository,
        briefingGenerationEngine = briefingGenerationEngine,
        briefingExecutionOrchestratorService = briefingExecutionOrchestratorService,
        idGenerator = idGenerator,
        objectMapper = objectMapper,
        executionEnabled = false
    )

    @Test
    fun `generateApprovedBriefing transitions to ready and persists citations references conflicts`() {
        val userId = UUID.randomUUID()
        val source = createSource(userId)
        val briefing = Briefing.create(UUID.randomUUID(), userId, BriefingEnrichmentIntent.TRUTH_GROUNDING).apply {
            approve()
        }
        val link = BriefingSource(UUID.randomUUID(), briefing.id, source.id, userId)
        val step = BriefingPlanStep(
            id = UUID.randomUUID(),
            briefingId = briefing.id,
            personaId = null,
            personaName = "Claim Auditor",
            stepOrder = 1,
            task = "Audit claims",
            status = BriefingPlanStepStatus.PLANNED
        )
        val referenceId = UUID.randomUUID()

        whenever(briefingRepository.findByIdAndUserId(briefing.id, userId)).thenReturn(briefing)
        whenever(briefingSourceRepository.findByBriefingIdOrderByCreatedAtAsc(briefing.id)).thenReturn(listOf(link))
        whenever(sourceRepository.findAllByUserIdAndIdIn(userId, listOf(source.id))).thenReturn(listOf(source))
        whenever(briefingPlanStepRepository.findByBriefingIdOrderByStepOrderAsc(briefing.id)).thenReturn(listOf(step))
        whenever(briefingRepository.save(any())).thenAnswer { it.arguments[0] as Briefing }
        whenever(briefingPlanStepRepository.saveAll(any<List<BriefingPlanStep>>())).thenAnswer { it.arguments[0] }
        whenever(idGenerator.newId()).thenReturn(referenceId)
        whenever(briefingReferenceRepository.saveAll(any<List<BriefingReference>>())).thenAnswer { it.arguments[0] }
        whenever(
            briefingGenerationEngine.generate(any())
        ).thenReturn(
            BriefingGenerationResult(
                markdownBody = "## Generated briefing [1]",
                references = listOf(
                    BriefingReferenceCandidate(
                        url = "https://external.example.com/ref",
                        title = "External Ref",
                        snippet = "Snippet"
                    )
                ),
                conflictHighlights = listOf(
                    BriefingConflictHighlightResponse(
                        claim = "Claim A",
                        counterClaim = "Claim B",
                        confidence = 0.9,
                        evidenceCitationLabels = listOf("[1]")
                    )
                )
            )
        )

        service.generateApprovedBriefing(briefing.id, userId)

        assertEquals(BriefingStatus.READY, briefing.status)
        assertNotNull(briefing.contentMarkdown)
        assertTrue(briefing.contentMarkdown!!.contains("## Citations"))
        assertNotNull(briefing.citationsJson)
        assertNotNull(briefing.conflictHighlightsJson)
        assertEquals(BriefingPlanStepStatus.SUCCEEDED, step.status)
    }

    @Test
    fun `generateApprovedBriefing transitions to failed when engine errors`() {
        val userId = UUID.randomUUID()
        val source = createSource(userId)
        val briefing = Briefing.create(UUID.randomUUID(), userId, BriefingEnrichmentIntent.DEEP_DIVE).apply {
            approve()
        }
        val link = BriefingSource(UUID.randomUUID(), briefing.id, source.id, userId)
        val step = BriefingPlanStep(
            id = UUID.randomUUID(),
            briefingId = briefing.id,
            personaId = null,
            personaName = "Synthesis Writer",
            stepOrder = 1,
            task = "Write synthesis",
            status = BriefingPlanStepStatus.PLANNED
        )

        whenever(briefingRepository.findByIdAndUserId(briefing.id, userId)).thenReturn(briefing)
        whenever(briefingSourceRepository.findByBriefingIdOrderByCreatedAtAsc(briefing.id)).thenReturn(listOf(link))
        whenever(sourceRepository.findAllByUserIdAndIdIn(userId, listOf(source.id))).thenReturn(listOf(source))
        whenever(briefingPlanStepRepository.findByBriefingIdOrderByStepOrderAsc(briefing.id)).thenReturn(listOf(step))
        whenever(briefingRepository.save(any())).thenAnswer { it.arguments[0] as Briefing }
        whenever(briefingPlanStepRepository.saveAll(any<List<BriefingPlanStep>>())).thenAnswer { it.arguments[0] }
        whenever(briefingGenerationEngine.generate(any())).thenThrow(RuntimeException("engine_down"))

        assertThrows<RuntimeException> {
            service.generateApprovedBriefing(briefing.id, userId)
        }

        assertEquals(BriefingStatus.FAILED, briefing.status)
        assertNotNull(briefing.errorJson)
        assertEquals(BriefingPlanStepStatus.FAILED, step.status)
    }

    private fun createSource(userId: UUID): Source {
        return Source(
            id = UUID.randomUUID(),
            url = Url.from("https://example.com/briefing-source"),
            status = SourceStatus.ACTIVE,
            content = Content.from("The source content used for briefing generation."),
            metadata = Metadata.from(
                title = "Briefing Source",
                author = "Author",
                publishedDate = Instant.now(),
                platform = "web",
                wordCount = 42,
                aiFormatted = true,
                extractionProvider = "jsoup"
            ),
            userId = userId,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
}
