package com.briefy.api.application.briefing

import org.springframework.stereotype.Component

@Component
class DeterministicBriefingGenerationEngine : BriefingGenerationEngine {
    override fun generate(request: BriefingGenerationRequest): BriefingGenerationResult {
        val sourceSummary = request.sources.mapIndexed { index, source ->
            "${index + 1}. ${source.title.ifBlank { source.url }}"
        }.joinToString("\n")

        val firstSourceText = request.sources.firstOrNull()?.text.orEmpty().trim()
        val excerpt = if (firstSourceText.length > 420) {
            firstSourceText.take(420) + "..."
        } else {
            firstSourceText
        }

        val inlineSourceRef = if (request.sources.isNotEmpty()) "[1]" else ""
        val markdownBody = buildString {
            appendLine("## Briefing")
            appendLine()
            appendLine("Intent: ${request.enrichmentIntent.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }}")
            appendLine()
            appendLine("This synthesis is grounded on the selected source set $inlineSourceRef.")
            if (excerpt.isNotBlank()) {
                appendLine()
                appendLine("### Source Excerpt")
                appendLine(excerpt)
            }
            appendLine()
            appendLine("### Selected Sources")
            appendLine(sourceSummary)
        }.trim()

        val conflictHighlights = if (
            request.enrichmentIntent.equals("truth_grounding", ignoreCase = true) && request.sources.size >= 2
        ) {
            listOf(
                BriefingConflictHighlightResponse(
                    claim = "The primary source emphasizes a single dominant interpretation.",
                    counterClaim = "Alternative readings suggest multiple viable interpretations across sources.",
                    confidence = 0.8,
                    evidenceCitationLabels = listOf("[1]", "[2]")
                )
            )
        } else {
            emptyList()
        }

        return BriefingGenerationResult(
            markdownBody = markdownBody,
            references = emptyList(),
            conflictHighlights = conflictHighlights
        )
    }
}
