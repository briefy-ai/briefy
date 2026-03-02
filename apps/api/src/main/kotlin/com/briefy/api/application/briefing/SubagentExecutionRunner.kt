package com.briefy.api.application.briefing

import java.util.UUID

interface SubagentExecutionRunner {
    fun execute(context: SubagentExecutionContext): SubagentExecutionResult
}

data class SubagentExecutionContext(
    val briefingId: UUID,
    val briefingRunId: UUID,
    val subagentRunId: UUID,
    val personaKey: String,
    val personaName: String,
    val task: String,
    val sources: List<BriefingSourceInput>
)

sealed interface SubagentExecutionResult {
    data class Succeeded(
        val curatedText: String,
        val sourceIdsUsedJson: String? = null,
        val referencesUsedJson: String? = null,
        val toolStatsJson: String? = null
    ) : SubagentExecutionResult

    object EmptyOutput : SubagentExecutionResult

    data class Failed(
        val errorCode: String,
        val errorMessage: String? = null
    ) : SubagentExecutionResult
}
