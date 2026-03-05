package com.briefy.api.application.briefing

import java.time.Instant
import java.util.UUID

data class BriefingRunSummaryResponse(
    val briefingRun: BriefingRunSnapshotResponse,
    val subagents: List<SubagentRunSnapshotResponse>,
    val synthesis: SynthesisRunSnapshotResponse,
    val metrics: BriefingRunMetricsResponse
)

data class BriefingRunSnapshotResponse(
    val id: UUID,
    val briefingId: UUID,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val startedAt: Instant?,
    val endedAt: Instant?,
    val deadlineAt: Instant?,
    val totalPersonas: Int,
    val requiredForSynthesis: Int,
    val nonEmptySucceededCount: Int,
    val cancelRequestedAt: Instant?,
    val failureCode: String?,
    val failureMessage: String?,
    val reusedFromRunId: UUID?
)

data class SubagentRunSnapshotResponse(
    val id: UUID,
    val personaKey: String,
    val status: String,
    val attempt: Int,
    val maxAttempts: Int,
    val startedAt: Instant?,
    val endedAt: Instant?,
    val deadlineAt: Instant?,
    val toolStats: Map<String, Any?>?,
    val lastError: SubagentLastErrorResponse?,
    val reused: Boolean
)

data class SubagentLastErrorResponse(
    val code: String?,
    val retryable: Boolean?,
    val message: String?
)

data class SynthesisRunSnapshotResponse(
    val id: UUID?,
    val status: String,
    val inputPersonaCount: Int,
    val includedPersonaKeys: List<String>,
    val excludedPersonaKeys: List<String>,
    val startedAt: Instant?,
    val endedAt: Instant?,
    val output: String?,
    val lastErrorCode: String?,
    val lastErrorMessage: String?
)

data class BriefingRunMetricsResponse(
    val durationMs: Long,
    val subagentSucceeded: Int,
    val subagentSkipped: Int,
    val subagentSkippedNoOutput: Int,
    val subagentFailed: Int,
    val toolCallsTotal: Long
)

data class BriefingRunEventsPageResponse(
    val items: List<BriefingRunEventResponse>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val limit: Int
)

data class BriefingRunEventResponse(
    val eventId: UUID,
    val eventType: String,
    val ts: Instant,
    val briefingRunId: UUID,
    val subagentRunId: UUID?,
    val attempt: Int?,
    val payload: Map<String, Any?>?
)
