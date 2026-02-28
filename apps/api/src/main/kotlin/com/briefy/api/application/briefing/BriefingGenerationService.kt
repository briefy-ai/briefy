package com.briefy.api.application.briefing

import com.briefy.api.domain.knowledgegraph.briefing.*
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.briefy.api.domain.knowledgegraph.source.SourceStatus
import com.briefy.api.infrastructure.id.IdGenerator
import com.fasterxml.jackson.databind.ObjectMapper
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
    private val idGenerator: IdGenerator,
    private val objectMapper: ObjectMapper
) {

    @Transactional
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

        val now = Instant.now()
        if (briefing.status == BriefingStatus.APPROVED) {
            briefing.startGeneration(now)
        }
        planSteps.forEach { step ->
            if (step.status == BriefingPlanStepStatus.PLANNED) {
                step.markRunning(now)
            }
        }
        briefingRepository.save(briefing)
        briefingPlanStepRepository.saveAll(planSteps)

        try {
            val generationResult = briefingGenerationEngine.generate(
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

            planSteps.forEach { step ->
                if (step.status == BriefingPlanStepStatus.RUNNING) {
                    step.markSucceeded()
                }
            }
            briefingPlanStepRepository.saveAll(planSteps)
        } catch (ex: Exception) {
            planSteps.forEach { step ->
                if (step.status == BriefingPlanStepStatus.RUNNING) {
                    step.markFailed()
                }
            }
            briefingPlanStepRepository.saveAll(planSteps)

            val errorPayload = BriefingErrorResponse(
                code = "generation_failed",
                message = ex.message ?: "Briefing generation failed",
                retryable = true,
                details = null
            )
            briefing.failGeneration(objectMapper.writeValueAsString(errorPayload))
            briefingRepository.save(briefing)
            throw ex
        }
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
        sources: List<com.briefy.api.domain.knowledgegraph.source.Source>,
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
