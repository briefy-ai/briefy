package com.briefy.api.application.briefing

import com.briefy.api.domain.knowledgegraph.briefing.*
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.infrastructure.id.IdGenerator
import com.briefy.api.infrastructure.security.CurrentUserProvider
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class BriefingService(
    private val briefingRepository: BriefingRepository,
    private val briefingRunRepository: BriefingRunRepository,
    private val briefingSourceRepository: BriefingSourceRepository,
    private val briefingPlanStepRepository: BriefingPlanStepRepository,
    private val briefingReferenceRepository: BriefingReferenceRepository,
    private val sourceRepository: SourceRepository,
    private val briefingPlannerService: BriefingPlannerService,
    private val briefingGenerationJobService: BriefingGenerationJobService,
    private val briefingGenerationJobRepository: BriefingGenerationJobRepository,
    private val runEventRepository: RunEventRepository,
    private val subagentRunRepository: SubagentRunRepository,
    private val synthesisRunRepository: SynthesisRunRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val idGenerator: IdGenerator,
    private val objectMapper: ObjectMapper
) {
    private val DEFAULT_BRIEFING_LIST_LIMIT = 20
    private val MAX_BRIEFING_LIST_LIMIT = 100

    @Transactional
    fun createBriefing(command: CreateBriefingCommand): BriefingResponse {
        val userId = currentUserProvider.requireUserId()
        val intent = parseIntent(command.enrichmentIntent)
        val sources = loadAndValidateSources(userId, command.sourceIds)

        val now = Instant.now()
        val briefing = Briefing.create(
            id = idGenerator.newId(),
            userId = userId,
            enrichmentIntent = intent,
            now = now
        )
        briefingRepository.save(briefing)

        val links = sources.map { source ->
            BriefingSource(
                id = idGenerator.newId(),
                briefingId = briefing.id,
                sourceId = source.id,
                userId = userId,
                createdAt = now
            )
        }
        briefingSourceRepository.saveAll(links)

        val planDrafts = briefingPlannerService.buildPlan(
            briefingId = briefing.id,
            userId = userId,
            enrichmentIntent = intent.name,
            sources = sources
        )

        // TODO: remove duplicate
        val planSteps = planDrafts.mapIndexed { index, draft ->
            BriefingPlanStep(
                id = idGenerator.newId(),
                briefingId = briefing.id,
                personaId = draft.personaId,
                personaName = draft.personaName,
                stepOrder = index + 1,
                task = draft.task,
                status = BriefingPlanStepStatus.PLANNED,
                createdAt = now,
                updatedAt = now
            )
        }
        briefingPlanStepRepository.saveAll(planSteps)
        briefing.markPlanned(now)
        briefingRepository.save(briefing)

        return toResponse(briefing, links, planSteps, emptyList())
    }

    @Transactional(readOnly = true)
    fun listBriefingsSummary(status: BriefingStatus?, limit: Int?, cursor: String?): BriefingPageResponse {
        val userId = currentUserProvider.requireUserId()
        val normalizedLimit = (limit ?: DEFAULT_BRIEFING_LIST_LIMIT).coerceIn(1, MAX_BRIEFING_LIST_LIMIT)
        val decodedCursor = cursor?.let { BriefingListCursorCodec.decode(it) }

        val briefings = briefingRepository.findBriefingsPage(
            userId = userId,
            status = status,
            cursorCreatedAt = decodedCursor?.createdAt,
            cursorId = decodedCursor?.id,
            limit = normalizedLimit
        )

        val hasMore = briefings.size > normalizedLimit
        val pageItems = if (hasMore) briefings.dropLast(1) else briefings

        val nextCursor = if (hasMore && pageItems.isNotEmpty()) {
            BriefingListCursorCodec.encode(BriefingListCursor(
                createdAt = pageItems.last().createdAt,
                id = pageItems.last().id
            ))
        } else {
            null
        }

        val items = pageItems.map { briefing ->
            BriefingSummaryResponse(
                id = briefing.id,
                status = briefing.status.name.lowercase(),
                enrichmentIntent = briefing.enrichmentIntent.name.lowercase(),
                title = briefing.title,
                sourceCount = briefingSourceRepository.countByBriefingId(briefing.id),
                contentSnippet = briefing.contentMarkdown?.take(200),
                createdAt = briefing.createdAt,
                updatedAt = briefing.updatedAt
            )
        }

        return BriefingPageResponse(
            items = items,
            nextCursor = nextCursor,
            hasMore = hasMore,
            limit = normalizedLimit
        )
    }

    @Transactional(readOnly = true)
    fun listBriefings(status: BriefingStatus?): List<BriefingResponse> {
        val userId = currentUserProvider.requireUserId()
        val briefings = if (status == null) {
            briefingRepository.findByUserIdOrderByUpdatedAtDesc(userId)
        } else {
            briefingRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(userId, status)
        }

        if (briefings.isEmpty()) {
            return emptyList()
        }

        val latestExecutionRunIds = latestExecutionRunIdsByBriefingId(briefings.map { it.id })
        val responses = mutableListOf<BriefingResponse>()
        briefings.forEach { briefing ->
            val links = briefingSourceRepository.findByBriefingIdOrderByCreatedAtAsc(briefing.id)
            val planSteps = briefingPlanStepRepository.findByBriefingIdOrderByStepOrderAsc(briefing.id)
            val references = briefingReferenceRepository.findByBriefingIdOrderByCreatedAtAsc(briefing.id)
            responses.add(
                toResponse(
                    briefing = briefing,
                    links = links,
                    planSteps = planSteps,
                    references = references,
                    executionRunId = latestExecutionRunIds[briefing.id]
                )
            )
        }
        return responses
    }

    @Transactional(readOnly = true)
    fun getBriefing(id: UUID): BriefingResponse {
        val userId = currentUserProvider.requireUserId()
        val briefing = briefingRepository.findByIdAndUserId(id, userId)
            ?: throw BriefingNotFoundException(id)
        val links = briefingSourceRepository.findByBriefingIdOrderByCreatedAtAsc(briefing.id)
        val planSteps = briefingPlanStepRepository.findByBriefingIdOrderByStepOrderAsc(briefing.id)
        val references = briefingReferenceRepository.findByBriefingIdOrderByCreatedAtAsc(briefing.id)
        return toResponse(briefing, links, planSteps, references)
    }

    @Transactional
    fun approvePlan(id: UUID): BriefingResponse {
        val userId = currentUserProvider.requireUserId()
        val briefing = briefingRepository.findByIdAndUserId(id, userId)
            ?: throw BriefingNotFoundException(id)

        if (briefing.status != BriefingStatus.PLAN_PENDING_APPROVAL) {
            throw InvalidBriefingStateException(
                "Can only approve plans in plan_pending_approval status. Current status: ${briefing.status.name.lowercase()}"
            )
        }

        val now = Instant.now()
        briefing.approve(now)
        briefingRepository.save(briefing)
        briefingGenerationJobService.enqueue(briefing.id, briefing.userId, now)

        val links = briefingSourceRepository.findByBriefingIdOrderByCreatedAtAsc(briefing.id)
        val planSteps = briefingPlanStepRepository.findByBriefingIdOrderByStepOrderAsc(briefing.id)
        val references = briefingReferenceRepository.findByBriefingIdOrderByCreatedAtAsc(briefing.id)
        return toResponse(briefing, links, planSteps, references)
    }

    @Transactional
    fun retryBriefing(id: UUID): BriefingResponse {
        val userId = currentUserProvider.requireUserId()
        val briefing = briefingRepository.findByIdAndUserId(id, userId)
            ?: throw BriefingNotFoundException(id)

        if (briefing.status != BriefingStatus.FAILED) {
            throw InvalidBriefingStateException(
                "Can only retry briefings in failed status. Current status: ${briefing.status.name.lowercase()}"
            )
        }

        val links = briefingSourceRepository.findByBriefingIdOrderByCreatedAtAsc(briefing.id)
        if (links.isEmpty()) {
            throw InvalidBriefingRequestException("Briefing must include at least one source")
        }

        val sources = loadAndValidateSources(userId, links.map { it.sourceId })

        briefingPlanStepRepository.deleteByBriefingId(briefing.id)
        briefingReferenceRepository.deleteByBriefingId(briefing.id)

        val now = Instant.now()
        briefing.resetForRetry(now)
        briefingRepository.save(briefing)

        val planDrafts = briefingPlannerService.buildPlan(
            briefingId = briefing.id,
            userId = userId,
            enrichmentIntent = briefing.enrichmentIntent.name,
            sources = sources
        )
        val planSteps = planDrafts.mapIndexed { index, draft ->
            BriefingPlanStep(
                id = idGenerator.newId(),
                briefingId = briefing.id,
                personaId = draft.personaId,
                personaName = draft.personaName,
                stepOrder = index + 1,
                task = draft.task,
                status = BriefingPlanStepStatus.PLANNED,
                createdAt = now,
                updatedAt = now
            )
        }
        briefingPlanStepRepository.saveAll(planSteps)

        return toResponse(briefing, links, planSteps, emptyList())
    }

    @Transactional
    fun deleteBriefing(id: UUID) {
        val userId = currentUserProvider.requireUserId()
        briefingRepository.findByIdAndUserId(id, userId)
            ?: throw BriefingNotFoundException(id)

        runEventRepository.deleteByBriefingId(id)
        subagentRunRepository.deleteByBriefingId(id)
        synthesisRunRepository.deleteByBriefingId(id)
        briefingRunRepository.deleteByBriefingId(id)
        briefingPlanStepRepository.deleteByBriefingId(id)
        briefingReferenceRepository.deleteByBriefingId(id)
        briefingSourceRepository.deleteByBriefingId(id)
        briefingGenerationJobRepository.deleteByBriefingId(id)
        briefingRepository.deleteById(id)
    }

    private fun loadAndValidateSources(userId: UUID, sourceIds: List<UUID>): List<Source> {
        val dedupedSourceIds = sourceIds.distinct()
        if (dedupedSourceIds.isEmpty()) {
            throw InvalidBriefingRequestException("sourceIds must contain at least one source")
        }

        val sourceById = sourceRepository.findAllByUserIdAndIdIn(userId, dedupedSourceIds)
            .associateBy { it.id }
        val orderedSources = dedupedSourceIds.map { sourceId ->
            sourceById[sourceId] ?: throw BriefingSourceAccessException()
        }

        if (orderedSources.any { it.status != SourceStatus.ACTIVE }) {
            throw InvalidBriefingRequestException("All sources must be active")
        }

        return orderedSources
    }

    private fun parseIntent(rawIntent: String): BriefingEnrichmentIntent {
        return try {
            BriefingEnrichmentIntent.fromApiValue(rawIntent)
        } catch (_: IllegalArgumentException) {
            throw InvalidBriefingRequestException("Invalid enrichmentIntent: $rawIntent")
        }
    }

    private fun toResponse(
        briefing: Briefing,
        links: List<BriefingSource>,
        planSteps: List<BriefingPlanStep>,
        references: List<BriefingReference>,
        executionRunId: UUID? = null
    ): BriefingResponse {
        val citations = parseList(briefing.citationsJson, object : TypeReference<List<BriefingCitationResponse>>() {})
        val conflictHighlights = parseList(
            briefing.conflictHighlightsJson,
            object : TypeReference<List<BriefingConflictHighlightResponse>>() {}
        )
        val error = parseValue(briefing.errorJson, BriefingErrorResponse::class.java)
        val latestExecutionRunId = executionRunId
            ?: briefingRunRepository.findTopByBriefingIdOrderByCreatedAtDesc(briefing.id)?.id

        return BriefingResponse(
            id = briefing.id,
            executionRunId = latestExecutionRunId,
            status = briefing.status.name.lowercase(),
            enrichmentIntent = briefing.enrichmentIntent.name.lowercase(),
            title = briefing.title,
            sourceIds = links.map { it.sourceId },
            plan = planSteps.map { step ->
                BriefingPlanStepResponse(
                    id = step.id,
                    personaId = step.personaId,
                    personaName = step.personaName,
                    task = step.task,
                    status = step.status.name.lowercase(),
                    stepOrder = step.stepOrder
                )
            },
            references = references.map { reference ->
                BriefingReferenceResponse(
                    id = reference.id,
                    url = reference.url,
                    title = reference.title,
                    snippet = reference.snippet,
                    status = reference.status.name.lowercase(),
                    promotedToSourceId = reference.promotedToSourceId
                )
            },
            contentMarkdown = briefing.contentMarkdown,
            citations = citations ?: emptyList(),
            conflictHighlights = conflictHighlights,
            error = error,
            createdAt = briefing.createdAt,
            updatedAt = briefing.updatedAt,
            plannedAt = briefing.plannedAt,
            approvedAt = briefing.approvedAt,
            generationStartedAt = briefing.generationStartedAt,
            generationCompletedAt = briefing.generationCompletedAt,
            failedAt = briefing.failedAt
        )
    }

    private fun latestExecutionRunIdsByBriefingId(briefingIds: List<UUID>): Map<UUID, UUID> {
        if (briefingIds.isEmpty()) {
            return emptyMap()
        }
        val runs = briefingRunRepository.findByBriefingIdInOrderByBriefingIdAscCreatedAtDesc(briefingIds)
        val latestByBriefingId = mutableMapOf<UUID, UUID>()
        runs.forEach { run ->
            latestByBriefingId.putIfAbsent(run.briefingId, run.id)
        }
        return latestByBriefingId
    }

    private fun <T> parseValue(json: String?, targetClass: Class<T>): T? {
        if (json.isNullOrBlank()) {
            return null
        }
        return runCatching { objectMapper.readValue(json, targetClass) }.getOrNull()
    }

    private fun <T> parseList(json: String?, typeReference: TypeReference<List<T>>): List<T>? {
        if (json.isNullOrBlank()) {
            return null
        }
        return runCatching { objectMapper.readValue(json, typeReference) }.getOrNull()
    }
}
