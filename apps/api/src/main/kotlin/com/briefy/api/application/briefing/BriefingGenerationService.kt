package com.briefy.api.application.briefing

import com.briefy.api.domain.knowledgegraph.briefing.*
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.infrastructure.id.IdGenerator
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class BriefingGenerationService(
    private val briefingRepository: BriefingRepository,
    private val briefingSourceRepository: BriefingSourceRepository,
    private val briefingPlanStepRepository: BriefingPlanStepRepository,
    private val briefingReferenceRepository: BriefingReferenceRepository,
    private val sourceRepository: SourceRepository,
    private val briefingGenerationEngine: BriefingGenerationEngine,
    private val briefingExecutionOrchestratorService: BriefingExecutionOrchestratorService,
    private val idGenerator: IdGenerator,
    private val objectMapper: ObjectMapper,
    @param:Value("\${briefing.execution.enabled:true}")
    private val executionEnabled: Boolean = true
) {

    @Transactional(noRollbackFor = [BriefingGenerationFailedException::class])
    fun generateApprovedBriefing(briefingId: UUID, userId: UUID) {
        val briefing = briefingRepository.findByIdAndUserId(briefingId, userId) ?: return
        if (briefing.status != BriefingStatus.APPROVED && briefing.status != BriefingStatus.GENERATING) {
            return
        }

        val sourceLinks = briefingSourceRepository.findByBriefingIdOrderByCreatedAtAsc(briefing.id)
        if (sourceLinks.isEmpty()) {
            throw InvalidBriefingRequestException("Briefing must include at least one source")
        }

        val sourceById = sourceRepository.findAllByUserIdAndIdIn(userId, sourceLinks.map { it.sourceId })
            .associateBy { it.id }
        val orderedSources = sourceLinks.map { link -> sourceById[link.sourceId] ?: throw BriefingSourceAccessException() }
        if (orderedSources.any { it.status != SourceStatus.ACTIVE }) {
            throw InvalidBriefingRequestException("All briefing sources must be active")
        }

        val planSteps = briefingPlanStepRepository.findByBriefingIdOrderByStepOrderAsc(briefing.id)

        if (briefing.status == BriefingStatus.APPROVED) {
            briefing.startGeneration(Instant.now())
            briefingRepository.save(briefing)
        }

        if (executionEnabled) {
            generateWithExecutionOrchestrator(
                briefing = briefing,
                orderedSources = orderedSources,
                planSteps = planSteps
            )
            return
        }

        generateWithLegacyEngine(
            briefing = briefing,
            orderedSources = orderedSources,
            planSteps = planSteps
        )
    }

    private fun generateWithExecutionOrchestrator(
        briefing: Briefing,
        orderedSources: List<Source>,
        planSteps: List<BriefingPlanStep>
    ) {
        val outcome = runCatching {
            briefingExecutionOrchestratorService.executeApprovedBriefing(
                briefing = briefing,
                orderedSources = orderedSources,
                planSteps = planSteps
            )
        }.getOrElse { error ->
            failAndThrow(
                briefing = briefing,
                planSteps = planSteps,
                code = BriefingRunFailureCode.ORCHESTRATION_ERROR.dbValue,
                message = error.message ?: "Briefing orchestration failed",
                cause = error
            )
        }

        if (outcome.status == ExecutionOrchestrationOutcome.Status.FAILED || outcome.generationResult == null) {
            failAndThrow(
                briefing = briefing,
                planSteps = planSteps,
                code = outcome.failureCode?.dbValue ?: BriefingRunFailureCode.ORCHESTRATION_ERROR.dbValue,
                message = outcome.failureMessage ?: "Briefing orchestration failed"
            )
        }

        persistSuccessResult(
            briefing = briefing,
            orderedSources = orderedSources,
            generationResult = outcome.generationResult
        )
    }

    private fun generateWithLegacyEngine(
        briefing: Briefing,
        orderedSources: List<Source>,
        planSteps: List<BriefingPlanStep>
    ) {
        val now = Instant.now()
        planSteps.forEach { step ->
            if (step.status == BriefingPlanStepStatus.PLANNED) {
                step.markRunning(now)
            }
        }
        briefingPlanStepRepository.saveAll(planSteps)

        val generationResult = runCatching {
            briefingGenerationEngine.generate(
                BriefingGenerationRequest(
                    briefingId = briefing.id,
                    userId = briefing.userId,
                    enrichmentIntent = briefing.enrichmentIntent.name,
                    sources = orderedSources.map { source ->
                        BriefingSourceInput(
                            sourceId = source.id,
                            title = source.metadata?.title ?: source.url.normalized,
                            url = source.url.normalized,
                            text = source.content?.text.orEmpty()
                        )
                    },
                    plan = planSteps.map { step ->
                        BriefingPlanInput(
                            personaName = step.personaName,
                            task = step.task,
                            stepOrder = step.stepOrder
                        )
                    }
                )
            )
        }.getOrElse { error ->
            failAndThrow(
                briefing = briefing,
                planSteps = planSteps,
                code = "generation_failed",
                message = error.message ?: "Briefing generation failed",
                cause = error
            )
        }

        persistSuccessResult(
            briefing = briefing,
            orderedSources = orderedSources,
            generationResult = generationResult
        )

        planSteps.forEach { step ->
            if (step.status == BriefingPlanStepStatus.RUNNING) {
                step.markSucceeded()
            }
        }
        briefingPlanStepRepository.saveAll(planSteps)
    }

    private fun persistSuccessResult(
        briefing: Briefing,
        orderedSources: List<Source>,
        generationResult: BriefingGenerationResult
    ) {
        val references = persistReferences(briefing, generationResult.references)
        val citations = buildCitations(orderedSources, references)
        val citationsJson = objectMapper.writeValueAsString(citations)
        val conflictHighlights = generationResult.conflictHighlights
            .filter { it.confidence >= CONFLICT_CONFIDENCE_THRESHOLD }
            .take(MAX_CONFLICT_HIGHLIGHTS)
        val conflictHighlightsJson = if (conflictHighlights.isEmpty()) {
            null
        } else {
            objectMapper.writeValueAsString(conflictHighlights)
        }

        val contentWithCitations = appendCitationsBlock(generationResult.markdownBody, citations)
        briefing.completeGeneration(contentWithCitations, citationsJson, conflictHighlightsJson)
        briefingRepository.save(briefing)
    }

    private fun failAndThrow(
        briefing: Briefing,
        planSteps: List<BriefingPlanStep>,
        code: String,
        message: String,
        cause: Throwable? = null
    ): Nothing {
        val now = Instant.now()
        planSteps.forEach { step ->
            if (step.status == BriefingPlanStepStatus.RUNNING) {
                step.markFailed(now)
            }
        }
        briefingPlanStepRepository.saveAll(planSteps)

        val errorPayload = BriefingErrorResponse(
            code = code,
            message = message,
            retryable = true,
            details = null
        )
        briefing.failGeneration(objectMapper.writeValueAsString(errorPayload), now)
        briefingRepository.save(briefing)

        throw BriefingGenerationFailedException(message, cause)
    }

    private fun persistReferences(
        briefing: Briefing,
        candidates: List<BriefingReferenceCandidate>
    ): List<BriefingReference> {
        if (candidates.isEmpty()) return emptyList()

        val deduped = linkedMapOf<String, BriefingReferenceCandidate>()
        candidates.forEach { candidate ->
            val normalizedUrl = candidate.url.trim()
            if (normalizedUrl.isBlank()) return@forEach
            deduped.putIfAbsent(normalizedUrl, candidate.copy(url = normalizedUrl))
        }

        if (deduped.isEmpty()) return emptyList()

        val now = Instant.now()
        val references = deduped.values.map { candidate ->
            BriefingReference(
                id = idGenerator.newId(),
                briefingId = briefing.id,
                userId = briefing.userId,
                url = candidate.url,
                title = candidate.title.takeIf { it.isNotBlank() } ?: candidate.url,
                snippet = candidate.snippet,
                status = BriefingReferenceStatus.ACTIVE,
                createdAt = now,
                updatedAt = now
            )
        }

        return briefingReferenceRepository.saveAll(references)
    }

    private fun buildCitations(
        sources: List<Source>,
        references: List<BriefingReference>
    ): List<BriefingCitationResponse> {
        val citations = mutableListOf<BriefingCitationResponse>()
        var nextIndex = 1

        sources.forEach { source ->
            citations.add(
                BriefingCitationResponse(
                    label = "[$nextIndex]",
                    type = "source",
                    title = source.metadata?.title ?: source.url.normalized,
                    url = "/sources/${source.id}",
                    sourceId = source.id,
                    referenceId = null
                )
            )
            nextIndex++
        }

        references.forEach { reference ->
            citations.add(
                BriefingCitationResponse(
                    label = "[$nextIndex]",
                    type = "reference",
                    title = reference.title,
                    url = reference.url,
                    sourceId = null,
                    referenceId = reference.id
                )
            )
            nextIndex++
        }

        return citations
    }

    private fun appendCitationsBlock(
        markdownBody: String,
        citations: List<BriefingCitationResponse>
    ): String {
        if (citations.isEmpty()) {
            return markdownBody.trim()
        }

        val citationsBlock = citations.joinToString("\n") { citation ->
            val target = citation.url ?: ""
            "- ${citation.label} ${citation.title} ($target)"
        }

        return buildString {
            append(markdownBody.trim())
            append("\n\n## Citations\n")
            append(citationsBlock)
        }
    }

    companion object {
        private const val MAX_CONFLICT_HIGHLIGHTS = 5
        private const val CONFLICT_CONFIDENCE_THRESHOLD = 0.75
    }
}
