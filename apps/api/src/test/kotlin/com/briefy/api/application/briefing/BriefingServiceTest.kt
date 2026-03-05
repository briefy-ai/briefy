package com.briefy.api.application.briefing

import com.briefy.api.domain.knowledgegraph.briefing.*
import com.briefy.api.domain.knowledgegraph.source.Content
import com.briefy.api.domain.knowledgegraph.source.Metadata
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.domain.knowledgegraph.source.Url
import com.briefy.api.infrastructure.id.IdGenerator
import com.briefy.api.infrastructure.security.CurrentUserProvider
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class BriefingServiceTest {
    private val briefingRepository: BriefingRepository = mock()
    private val briefingRunRepository: BriefingRunRepository = mock()
    private val briefingSourceRepository: BriefingSourceRepository = mock()
    private val briefingPlanStepRepository: BriefingPlanStepRepository = mock()
    private val briefingReferenceRepository: BriefingReferenceRepository = mock()
    private val sourceRepository: SourceRepository = mock()
    private val briefingPlannerService: BriefingPlannerService = mock()
    private val briefingGenerationJobService: BriefingGenerationJobService = mock()
    private val currentUserProvider: CurrentUserProvider = mock()
    private val idGenerator: IdGenerator = mock()
    private val objectMapper = ObjectMapper()

    private val service = BriefingService(
        briefingRepository = briefingRepository,
        briefingRunRepository = briefingRunRepository,
        briefingSourceRepository = briefingSourceRepository,
        briefingPlanStepRepository = briefingPlanStepRepository,
        briefingReferenceRepository = briefingReferenceRepository,
        sourceRepository = sourceRepository,
        briefingPlannerService = briefingPlannerService,
        briefingGenerationJobService = briefingGenerationJobService,
        currentUserProvider = currentUserProvider,
        idGenerator = idGenerator,
        objectMapper = objectMapper
    )

    @Test
    fun `createBriefing rejects empty source ids`() {
        whenever(currentUserProvider.requireUserId()).thenReturn(UUID.randomUUID())

        assertThrows(InvalidBriefingRequestException::class.java) {
            service.createBriefing(CreateBriefingCommand(emptyList(), "deep_dive"))
        }
    }

    @Test
    fun `createBriefing generates plan from intent`() {
        val userId = UUID.randomUUID()
        val source = createSource(userId, SourceStatus.ACTIVE)
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(sourceRepository.findAllByUserIdAndIdIn(userId, listOf(source.id))).thenReturn(listOf(source))
        whenever(idGenerator.newId()).thenReturn(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID()
        )
        whenever(briefingRepository.save(any())).thenAnswer { it.arguments[0] as Briefing }
        whenever(briefingSourceRepository.saveAll(any<List<BriefingSource>>())).thenAnswer { it.arguments[0] }
        whenever(briefingPlanStepRepository.saveAll(any<List<BriefingPlanStep>>())).thenAnswer { it.arguments[0] }
        whenever(
            briefingPlannerService.buildPlan(
                userId = userId,
                enrichmentIntent = "DEEP_DIVE",
                sources = listOf(source)
            )
        ).thenReturn(
            listOf(
                BriefingPlanDraft(null, "Synthesis Writer", "Write briefing")
            )
        )

        val response = service.createBriefing(
            CreateBriefingCommand(
                sourceIds = listOf(source.id),
                enrichmentIntent = "deep_dive"
            )
        )

        assertEquals("plan_pending_approval", response.status)
        assertEquals(1, response.plan.size)
        assertEquals("Synthesis Writer", response.plan.first().personaName)
        verify(briefingGenerationJobService, never()).enqueue(any(), any(), any())
    }

    @Test
    fun `approvePlan enqueues async generation job`() {
        val userId = UUID.randomUUID()
        val briefing = Briefing.create(UUID.randomUUID(), userId, BriefingEnrichmentIntent.DEEP_DIVE)
        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(briefingRepository.findByIdAndUserId(briefing.id, userId)).thenReturn(briefing)
        whenever(briefingRepository.save(any())).thenAnswer { it.arguments[0] as Briefing }
        whenever(briefingSourceRepository.findByBriefingIdOrderByCreatedAtAsc(briefing.id)).thenReturn(emptyList())
        whenever(briefingPlanStepRepository.findByBriefingIdOrderByStepOrderAsc(briefing.id)).thenReturn(emptyList())
        whenever(briefingReferenceRepository.findByBriefingIdOrderByCreatedAtAsc(briefing.id)).thenReturn(emptyList())

        val response = service.approvePlan(briefing.id)

        assertEquals("approved", response.status)
        verify(briefingGenerationJobService).enqueue(eq(briefing.id), eq(userId), any())
    }

    @Test
    fun `retryBriefing clears previous plan and references and regenerates plan`() {
        val userId = UUID.randomUUID()
        val source = createSource(userId, SourceStatus.ACTIVE)
        val briefing = Briefing.create(UUID.randomUUID(), userId, BriefingEnrichmentIntent.TRUTH_GROUNDING).apply {
            approve()
            startGeneration()
            failGeneration("{\"code\":\"generation_failed\"}")
        }
        val link = BriefingSource(UUID.randomUUID(), briefing.id, source.id, userId)

        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(briefingRepository.findByIdAndUserId(briefing.id, userId)).thenReturn(briefing)
        whenever(briefingSourceRepository.findByBriefingIdOrderByCreatedAtAsc(briefing.id)).thenReturn(listOf(link))
        whenever(sourceRepository.findAllByUserIdAndIdIn(userId, listOf(source.id))).thenReturn(listOf(source))
        whenever(briefingRepository.save(any())).thenAnswer { it.arguments[0] as Briefing }
        whenever(idGenerator.newId()).thenReturn(UUID.randomUUID())
        whenever(
            briefingPlannerService.buildPlan(
                userId = userId,
                enrichmentIntent = "TRUTH_GROUNDING",
                sources = listOf(source)
            )
        ).thenReturn(
            listOf(
                BriefingPlanDraft(null, "Claim Auditor", "Audit claims")
            )
        )

        val response = service.retryBriefing(briefing.id)

        assertEquals("plan_pending_approval", response.status)
        assertEquals(1, response.plan.size)
        verify(briefingPlanStepRepository).deleteByBriefingId(briefing.id)
        verify(briefingReferenceRepository).deleteByBriefingId(briefing.id)
    }

    @Test
    fun `listBriefings resolves execution run ids in one bulk lookup`() {
        val userId = UUID.randomUUID()
        val firstBriefing = Briefing.create(UUID.randomUUID(), userId, BriefingEnrichmentIntent.DEEP_DIVE)
        val secondBriefing = Briefing.create(UUID.randomUUID(), userId, BriefingEnrichmentIntent.TRUTH_GROUNDING)
        val firstLatestRun = createRun(firstBriefing.id, Instant.parse("2026-03-05T10:00:00Z"))
        val firstOlderRun = createRun(firstBriefing.id, Instant.parse("2026-03-05T09:00:00Z"))
        val secondLatestRun = createRun(secondBriefing.id, Instant.parse("2026-03-05T10:30:00Z"))

        whenever(currentUserProvider.requireUserId()).thenReturn(userId)
        whenever(briefingRepository.findByUserIdOrderByUpdatedAtDesc(userId)).thenReturn(listOf(firstBriefing, secondBriefing))
        whenever(
            briefingRunRepository.findByBriefingIdInOrderByBriefingIdAscCreatedAtDesc(
                listOf(firstBriefing.id, secondBriefing.id)
            )
        ).thenReturn(listOf(firstLatestRun, firstOlderRun, secondLatestRun))
        whenever(briefingSourceRepository.findByBriefingIdOrderByCreatedAtAsc(any())).thenReturn(emptyList())
        whenever(briefingPlanStepRepository.findByBriefingIdOrderByStepOrderAsc(any())).thenReturn(emptyList())
        whenever(briefingReferenceRepository.findByBriefingIdOrderByCreatedAtAsc(any())).thenReturn(emptyList())

        val responses = service.listBriefings(status = null)

        assertEquals(2, responses.size)
        val byId = responses.associateBy { it.id }
        assertEquals(firstLatestRun.id, byId[firstBriefing.id]?.executionRunId)
        assertEquals(secondLatestRun.id, byId[secondBriefing.id]?.executionRunId)
        verify(briefingRunRepository).findByBriefingIdInOrderByBriefingIdAscCreatedAtDesc(
            listOf(firstBriefing.id, secondBriefing.id)
        )
        verify(briefingRunRepository, never()).findTopByBriefingIdOrderByCreatedAtDesc(any())
    }

    private fun createSource(userId: UUID, status: SourceStatus): Source {
        return Source(
            id = UUID.randomUUID(),
            url = Url.from("https://example.com/article"),
            status = status,
            content = Content.from("Example source content"),
            metadata = Metadata.from(
                title = "Example Title",
                author = "Author",
                publishedDate = Instant.now(),
                platform = "web",
                wordCount = 100,
                aiFormatted = true,
                extractionProvider = "jsoup"
            ),
            userId = userId,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }

    private fun createRun(briefingId: UUID, createdAt: Instant): BriefingRun {
        return BriefingRun(
            id = UUID.randomUUID(),
            briefingId = briefingId,
            executionFingerprint = "fingerprint-${UUID.randomUUID()}",
            status = BriefingRunStatus.RUNNING,
            createdAt = createdAt,
            updatedAt = createdAt,
            totalPersonas = 1,
            requiredForSynthesis = 1
        )
    }
}
