package com.briefy.api.application.briefing

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

@Component
class DeterministicSequentialSubagentExecutionRunner(
    private val objectMapper: ObjectMapper
) : SubagentExecutionRunner {

    override fun execute(context: SubagentExecutionContext): SubagentExecutionResult {
        val normalizedTask = context.task.lowercase()
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
}
