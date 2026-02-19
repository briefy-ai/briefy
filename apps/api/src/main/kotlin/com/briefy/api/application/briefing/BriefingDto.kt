package com.briefy.api.application.briefing

import java.time.Instant
import java.util.UUID

data class CreateBriefingCommand(
    val sourceIds: List<UUID>,
    val enrichmentIntent: String
)

data class BriefingResponse(
    val id: UUID,
    val status: String,
    val enrichmentIntent: String,
    val sourceIds: List<UUID>,
    val plan: List<BriefingPlanStepResponse>,
    val references: List<BriefingReferenceResponse>,
    val contentMarkdown: String?,
    val citations: List<BriefingCitationResponse>,
    val conflictHighlights: List<BriefingConflictHighlightResponse>?,
    val error: BriefingErrorResponse?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val plannedAt: Instant?,
    val approvedAt: Instant?,
    val generationStartedAt: Instant?,
    val generationCompletedAt: Instant?,
    val failedAt: Instant?
)

data class BriefingPlanStepResponse(
    val id: UUID,
    val personaId: UUID?,
    val personaName: String,
    val task: String,
    val status: String,
    val stepOrder: Int
)

data class BriefingReferenceResponse(
    val id: UUID,
    val url: String,
    val title: String,
    val snippet: String?,
    val status: String,
    val promotedToSourceId: UUID?
)

data class BriefingCitationResponse(
    val label: String,
    val type: String,
    val title: String,
    val url: String?,
    val sourceId: UUID?,
    val referenceId: UUID?
)

data class BriefingConflictHighlightResponse(
    val claim: String,
    val counterClaim: String,
    val confidence: Double,
    val evidenceCitationLabels: List<String>
)

data class BriefingErrorResponse(
    val code: String,
    val message: String,
    val retryable: Boolean,
    val details: Map<String, String>?
)

data class BriefingPlanDraft(
    val personaId: UUID?,
    val personaName: String,
    val task: String
)

data class BriefingGenerationRequest(
    val briefingId: UUID,
    val userId: UUID,
    val enrichmentIntent: String,
    val sources: List<BriefingSourceInput>,
    val plan: List<BriefingPlanInput>
)

data class BriefingSourceInput(
    val sourceId: UUID,
    val title: String,
    val url: String,
    val text: String
)

data class BriefingPlanInput(
    val personaName: String,
    val task: String,
    val stepOrder: Int
)

data class BriefingReferenceCandidate(
    val url: String,
    val title: String,
    val snippet: String?
)

data class BriefingGenerationResult(
    val markdownBody: String,
    val references: List<BriefingReferenceCandidate>,
    val conflictHighlights: List<BriefingConflictHighlightResponse>
)
