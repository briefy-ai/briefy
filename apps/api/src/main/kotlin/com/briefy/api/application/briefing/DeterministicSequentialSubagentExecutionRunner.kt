package com.briefy.api.application.briefing

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class DeterministicSequentialSubagentExecutionRunner(
    private val objectMapper: ObjectMapper
) : SubagentExecutionRunner {

    override fun execute(context: SubagentExecutionContext): SubagentExecutionResult {
        val normalizedTask = context.task.lowercase()
        if (normalizedTask.contains("[transient:timeout]")) {
            return SubagentExecutionResult.Failed(
                errorCode = "timeout",
                errorMessage = "Deterministic transient timeout"
            )
        }
        if (normalizedTask.contains("[transient:429]")) {
            val retryAfter = parseRetryAfterSeconds(normalizedTask)
            return SubagentExecutionResult.Failed(
                errorCode = "http_429",
                errorMessage = retryAfter?.let { "retry_after=$it" } ?: "Deterministic transient 429"
            )
        }
        if (normalizedTask.contains("[transient:5xx]")) {
            return SubagentExecutionResult.Failed(
                errorCode = "http_5xx",
                errorMessage = "Deterministic transient server error"
            )
        }
        if (normalizedTask.contains("[transient:network]")) {
            return SubagentExecutionResult.Failed(
                errorCode = "network_error",
                errorMessage = "Deterministic transient network error"
            )
        }
        if (normalizedTask.contains("[fail]") || normalizedTask.contains("force_fail")) {
            return SubagentExecutionResult.Failed(
                errorCode = "deterministic_failure",
                errorMessage = "Deterministic runner forced a non-retryable failure"
            )
        }
        if (normalizedTask.contains("[empty]") || normalizedTask.contains("[skip]") || normalizedTask.contains("no_output")) {
            return SubagentExecutionResult.EmptyOutput
        }

        val firstSourceText = context.sources.firstOrNull()?.text.orEmpty().trim()
        val excerpt = when {
            firstSourceText.isBlank() -> "No source excerpt available"
            firstSourceText.length > MAX_EXCERPT_CHARS -> firstSourceText.take(MAX_EXCERPT_CHARS) + "..."
            else -> firstSourceText
        }

        val curatedText = buildString {
            appendLine("### ${context.personaName}")
            appendLine(context.task)
            appendLine()
            appendLine("Evidence excerpt:")
            appendLine(excerpt)
        }.trim()

        val sourceIdsUsedJson = objectMapper.writeValueAsString(context.sources.map { it.sourceId })
        val toolStatsJson = objectMapper.writeValueAsString(
            mapOf(
                "runner" to "deterministic_sequential",
                "sourceCount" to context.sources.size
            )
        )

        return SubagentExecutionResult.Succeeded(
            curatedText = curatedText,
            sourceIdsUsedJson = sourceIdsUsedJson,
            referencesUsedJson = null,
            toolStatsJson = toolStatsJson
        )
    }

    companion object {
        private const val MAX_EXCERPT_CHARS = 320
    }

    private fun parseRetryAfterSeconds(normalizedTask: String): Long? {
        val marker = "[transient:429:"
        val markerIndex = normalizedTask.indexOf(marker)
        if (markerIndex == -1) {
            return null
        }
        val valueStart = markerIndex + marker.length
        val valueEnd = normalizedTask.indexOf("]", valueStart)
        if (valueEnd == -1) {
            return null
        }
        return normalizedTask.substring(valueStart, valueEnd).toLongOrNull()
    }
}
