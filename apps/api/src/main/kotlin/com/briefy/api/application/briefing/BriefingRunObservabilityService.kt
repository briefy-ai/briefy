package com.briefy.api.application.briefing

import com.briefy.api.domain.knowledgegraph.briefing.*
import com.briefy.api.infrastructure.security.CurrentUserProvider
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class BriefingRunObservabilityService(
    private val briefingRunRepository: BriefingRunRepository,
    private val briefingRepository: BriefingRepository,
    private val subagentRunRepository: SubagentRunRepository,
    private val synthesisRunRepository: SynthesisRunRepository,
    private val runEventRepository: RunEventRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val objectMapper: ObjectMapper
) {

    @Transactional(readOnly = true)
    fun getRunSummary(runId: UUID): BriefingRunSummaryResponse {
        val briefingRun = loadAccessibleRun(runId)
        val subagentRuns = subagentRunRepository.findByBriefingRunIdOrderByCreatedAtAsc(briefingRun.id)
        val synthesisRun = synthesisRunRepository.findByBriefingRunId(briefingRun.id)
        val toolCallsTotal = runEventRepository.countByBriefingRunIdAndEventType(
            briefingRun.id,
            SUBAGENT_TOOL_CALL_STARTED_EVENT
        )

        val now = Instant.now()
        val durationMs = if (briefingRun.startedAt == null) {
            0L
        } else {
            Duration.between(briefingRun.startedAt, briefingRun.endedAt ?: now).toMillis().coerceAtLeast(0)
        }

        return BriefingRunSummaryResponse(
            briefingRun = BriefingRunSnapshotResponse(
                id = briefingRun.id,
                briefingId = briefingRun.briefingId,
                status = briefingRun.status.dbValue,
                createdAt = briefingRun.createdAt,
                updatedAt = briefingRun.updatedAt,
                startedAt = briefingRun.startedAt,
                endedAt = briefingRun.endedAt,
                deadlineAt = briefingRun.deadlineAt,
                totalPersonas = briefingRun.totalPersonas,
                requiredForSynthesis = briefingRun.requiredForSynthesis,
                nonEmptySucceededCount = briefingRun.nonEmptySucceededCount,
                cancelRequestedAt = briefingRun.cancelRequestedAt,
                failureCode = briefingRun.failureCode?.dbValue,
                failureMessage = briefingRun.failureMessage,
                reusedFromRunId = briefingRun.reusedFromRunId
            ),
            subagents = subagentRuns.map { subagentRun ->
                val lastError = if (
                    subagentRun.lastErrorCode != null ||
                    subagentRun.lastErrorRetryable != null ||
                    subagentRun.lastErrorMessage != null
                ) {
                    SubagentLastErrorResponse(
                        code = subagentRun.lastErrorCode,
                        retryable = subagentRun.lastErrorRetryable,
                        message = subagentRun.lastErrorMessage
                    )
                } else {
                    null
                }

                SubagentRunSnapshotResponse(
                    id = subagentRun.id,
                    personaKey = subagentRun.personaKey,
                    status = subagentRun.status.dbValue,
                    attempt = subagentRun.attempt,
                    maxAttempts = subagentRun.maxAttempts,
                    startedAt = subagentRun.startedAt,
                    endedAt = subagentRun.endedAt,
                    deadlineAt = subagentRun.deadlineAt,
                    toolStats = parseMap(subagentRun.toolStatsJson),
                    lastError = lastError,
                    reused = subagentRun.reused
                )
            },
            synthesis = mapSynthesisRun(synthesisRun),
            metrics = BriefingRunMetricsResponse(
                durationMs = durationMs,
                subagentSucceeded = subagentRuns.count { it.status == SubagentRunStatus.SUCCEEDED },
                subagentSkipped = subagentRuns.count { it.status == SubagentRunStatus.SKIPPED },
                subagentSkippedNoOutput = subagentRuns.count { it.status == SubagentRunStatus.SKIPPED_NO_OUTPUT },
                subagentFailed = subagentRuns.count { it.status == SubagentRunStatus.FAILED },
                toolCallsTotal = toolCallsTotal
            )
        )
    }

    @Transactional(readOnly = true)
    fun listRunEvents(runId: UUID, limit: Int?, cursor: String?): BriefingRunEventsPageResponse {
        val briefingRun = loadAccessibleRun(runId)
        val normalizedLimit = normalizeLimit(limit)
        val pageSize = normalizedLimit + 1
        val pageable = PageRequest.of(0, pageSize)
        val cursorValue = cursor?.let { RunEventPageCursorCodec.decode(it) }

        val orderedEvents = if (cursorValue == null) {
            runEventRepository.findPageByBriefingRunId(briefingRun.id, pageable)
        } else {
            runEventRepository.findPageByBriefingRunIdAfterCursor(
                briefingRunId = briefingRun.id,
                cursorOccurredAt = cursorValue.occurredAt,
                cursorSequenceId = cursorValue.sequenceId,
                pageable = pageable
            )
        }

        val hasMore = orderedEvents.size > normalizedLimit
        val pageEvents = if (hasMore) orderedEvents.take(normalizedLimit) else orderedEvents
        val nextCursor = if (hasMore && pageEvents.isNotEmpty()) {
            val last = pageEvents.last()
            RunEventPageCursorCodec.encode(
                RunEventPageCursor(
                    occurredAt = last.occurredAt,
                    sequenceId = last.sequenceId
                )
            )
        } else {
            null
        }

        return BriefingRunEventsPageResponse(
            items = pageEvents.map { event ->
                BriefingRunEventResponse(
                    eventId = event.eventId,
                    eventType = event.eventType,
                    ts = event.occurredAt,
                    briefingRunId = event.briefingRunId,
                    subagentRunId = event.subagentRunId,
                    attempt = event.attempt,
                    payload = parseMap(event.payloadJson)
                )
            },
            nextCursor = nextCursor,
            hasMore = hasMore,
            limit = normalizedLimit
        )
    }

    private fun mapSynthesisRun(run: SynthesisRun?): SynthesisRunSnapshotResponse {
        if (run == null) {
            return SynthesisRunSnapshotResponse(
                id = null,
                status = SynthesisRunStatus.NOT_STARTED.dbValue,
                inputPersonaCount = 0,
                includedPersonaKeys = emptyList(),
                excludedPersonaKeys = emptyList(),
                startedAt = null,
                endedAt = null,
                output = null,
                lastErrorCode = null,
                lastErrorMessage = null
            )
        }

        return SynthesisRunSnapshotResponse(
            id = run.id,
            status = run.status.dbValue,
            inputPersonaCount = run.inputPersonaCount,
            includedPersonaKeys = parseStringList(run.includedPersonaKeysJson),
            excludedPersonaKeys = parseStringList(run.excludedPersonaKeysJson),
            startedAt = run.startedAt,
            endedAt = run.endedAt,
            output = run.output,
            lastErrorCode = run.lastErrorCode,
            lastErrorMessage = run.lastErrorMessage
        )
    }

    private fun loadAccessibleRun(runId: UUID): BriefingRun {
        val userId = currentUserProvider.requireUserId()
        val run = briefingRunRepository.findById(runId).orElseThrow {
            ExecutionRunNotFoundException("BriefingRun", runId)
        }
        val briefing = briefingRepository.findByIdAndUserId(run.briefingId, userId)
        if (briefing == null) {
            throw ExecutionRunNotFoundException("BriefingRun", runId)
        }
        return run
    }

    private fun parseMap(json: String?): Map<String, Any?>? {
        if (json.isNullOrBlank()) {
            return null
        }
        return runCatching {
            objectMapper.readValue(json, object : TypeReference<Map<String, Any?>>() {})
        }.getOrNull()
    }

    private fun parseStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) {
            return emptyList()
        }
        return runCatching {
            objectMapper.readValue(json, object : TypeReference<List<String>>() {})
        }.getOrDefault(emptyList())
    }

    private fun normalizeLimit(limit: Int?): Int {
        if (limit == null) {
            return DEFAULT_LIMIT
        }
        require(limit in 1..MAX_LIMIT) { "limit must be between 1 and $MAX_LIMIT" }
        return limit
    }

    companion object {
        private const val SUBAGENT_TOOL_CALL_STARTED_EVENT = "subagent.tool_call.started"
        private const val DEFAULT_LIMIT = 50
        private const val MAX_LIMIT = 200
    }
}
