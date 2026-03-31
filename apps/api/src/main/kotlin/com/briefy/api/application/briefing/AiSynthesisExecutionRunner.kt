package com.briefy.api.application.briefing

import com.briefy.api.infrastructure.ai.AiAdapter
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

class AiSynthesisExecutionRunner(
    private val aiAdapter: AiAdapter,
    private val objectMapper: ObjectMapper,
    private val config: AiRunnerConfig
) : SynthesisExecutionRunner {

    override fun run(request: BriefingGenerationRequest): BriefingGenerationResult {
        val aggregatedReferences = mergeReferences(
            primary = request.subagentOutputs.flatMap { it.references },
            fallback = emptyList()
        )

        val response = aiAdapter.complete(
            provider = config.provider,
            model = config.model,
            prompt = buildUserPrompt(request, aggregatedReferences),
            systemPrompt = buildSystemPrompt(),
            useCase = "synthesis_execution"
        )

        val structured = parseStructuredResponse(response)
        if (structured != null) {
            return BriefingGenerationResult(
                markdownBody = structured.markdownBody,
                references = mergeReferences(structured.references, aggregatedReferences),
                conflictHighlights = structured.conflictHighlights,
                title = structured.title
            )
        }

        val markdownBody = extractMarkdownBody(response).ifBlank { buildDeterministicFallbackMarkdown(request) }
        return BriefingGenerationResult(
            markdownBody = markdownBody,
            references = aggregatedReferences,
            conflictHighlights = emptyList(),
            title = null
        )
    }

    private fun buildSystemPrompt(): String {
        return """You synthesize multi-persona research into one cohesive briefing.

Requirements:
1. Resolve disagreements explicitly; do not hide conflicts.
2. Keep claims evidence-grounded and avoid speculation.
3. Return valid JSON only, no prose before/after.
4. JSON schema:
{
  "title": "string",
  "markdownBody": "string",
  "references": [{"url":"string","title":"string","snippet":"string|null"}],
  "conflictHighlights": [{
    "claim":"string",
    "counterClaim":"string",
    "confidence":0.0,
    "evidenceCitationLabels":["[1]"]
  }]
}

Notes:
- `title` is a concise, descriptive title for the briefing (max 120 chars). Do not use generic titles like "Briefing" or "Summary".
- `markdownBody` must be non-empty markdown.
- `references` should include external references used in the synthesis.
- `confidence` is between 0 and 1.
- If there are no material conflicts, return an empty `conflictHighlights` array."""
    }

    private fun buildUserPrompt(
        request: BriefingGenerationRequest,
        aggregatedReferences: List<BriefingReferenceCandidate>
    ): String {
        val sourcesSection = request.sources.mapIndexed { index, source ->
            "${index + 1}. ${source.title.ifBlank { source.url }} (${source.url})"
        }.joinToString("\n")

        val outputsSection = if (request.subagentOutputs.isEmpty()) {
            "No successful persona outputs available. Produce the best synthesis from source metadata only."
        } else {
            request.subagentOutputs.joinToString("\n\n") { output ->
                val text = output.curatedText.trim().let {
                    if (it.length > MAX_PERSONA_OUTPUT_CHARS) it.take(MAX_PERSONA_OUTPUT_CHARS) + "..." else it
                }
                buildString {
                    appendLine("### ${output.personaName} (${output.personaKey})")
                    appendLine("Task: ${output.task}")
                    appendLine(text)
                }.trim()
            }
        }

        val referencesSection = if (aggregatedReferences.isEmpty()) {
            "No external references from subagents."
        } else {
            aggregatedReferences.mapIndexed { index, reference ->
                "${index + 1}. ${reference.title} (${reference.url})"
            }.joinToString("\n")
        }

        return """Intent: ${request.enrichmentIntent}

Sources:
$sourcesSection

Successful Persona Outputs:
$outputsSection

External References Collected by Personas:
$referencesSection

Produce a single, coherent markdown briefing that reconciles disagreement where needed.
Return JSON only with the required schema."""
    }

    private fun parseStructuredResponse(response: String): StructuredSynthesisResponse? {
        val root = parseResponseJson(response) ?: return null

        val markdownNode = root.path("markdownBody")
        if (!markdownNode.isTextual) {
            return null
        }

        val markdownBody = markdownNode.asText().trim()
        if (markdownBody.isBlank()) {
            return null
        }

        return StructuredSynthesisResponse(
            title = nullableText(root.path("title")),
            markdownBody = markdownBody,
            references = parseReferences(root.path("references")),
            conflictHighlights = parseConflictHighlights(root.path("conflictHighlights"))
        )
    }

    private fun parseResponseJson(response: String): JsonNode? {
        val trimmed = response.trim()
        val candidates = listOf(
            trimmed,
            extractJsonBlock(trimmed)
        )

        for (candidate in candidates) {
            if (candidate.isBlank()) {
                continue
            }
            val parsed = runCatching { objectMapper.readTree(candidate) }.getOrNull()
            if (parsed != null && parsed.isObject) {
                return parsed
            }
        }
        return null
    }

    private fun extractJsonBlock(response: String): String {
        val match = JSON_BLOCK_REGEX.find(response) ?: return ""
        return match.groupValues[1].trim()
    }

    private fun parseReferences(node: JsonNode): List<BriefingReferenceCandidate> {
        if (!node.isArray) {
            return emptyList()
        }
        return node.mapNotNull { entry ->
            val url = entry.path("url").asText().trim()
            if (url.isBlank()) {
                return@mapNotNull null
            }
            val title = entry.path("title").asText().trim().ifBlank { url }
            val snippet = nullableText(entry.path("snippet"))
            BriefingReferenceCandidate(
                url = url,
                title = title,
                snippet = snippet
            )
        }
    }

    private fun parseConflictHighlights(node: JsonNode): List<BriefingConflictHighlightResponse> {
        if (!node.isArray) {
            return emptyList()
        }
        return node.mapNotNull { entry ->
            val claim = entry.path("claim").asText().trim()
            val counterClaim = entry.path("counterClaim").asText().trim()
            if (claim.isBlank() || counterClaim.isBlank()) {
                return@mapNotNull null
            }
            val confidence = entry.path("confidence").asDouble(0.0).coerceIn(0.0, 1.0)
            val evidenceCitationLabels = if (entry.path("evidenceCitationLabels").isArray) {
                entry.path("evidenceCitationLabels")
                    .mapNotNull { labelNode -> labelNode.asText().trim().takeIf { it.isNotBlank() } }
            } else {
                emptyList()
            }

            BriefingConflictHighlightResponse(
                claim = claim,
                counterClaim = counterClaim,
                confidence = confidence,
                evidenceCitationLabels = evidenceCitationLabels
            )
        }
    }

    private fun mergeReferences(
        primary: List<BriefingReferenceCandidate>,
        fallback: List<BriefingReferenceCandidate>
    ): List<BriefingReferenceCandidate> {
        val deduped = linkedMapOf<String, BriefingReferenceCandidate>()
        (primary + fallback).forEach { candidate ->
            val url = candidate.url.trim()
            if (url.isBlank()) {
                return@forEach
            }
            val key = url.lowercase()
            if (deduped.containsKey(key)) {
                return@forEach
            }
            deduped[key] = BriefingReferenceCandidate(
                url = url,
                title = candidate.title.trim().ifBlank { url },
                snippet = candidate.snippet?.trim()?.takeIf { it.isNotBlank() }
            )
        }
        return deduped.values.toList()
    }

    private fun nullableText(node: JsonNode): String? {
        if (node.isMissingNode || node.isNull) {
            return null
        }
        return node.asText().trim().takeIf { it.isNotBlank() }
    }

    private fun extractMarkdownBody(response: String): String {
        val outputBlock = OUTPUT_BLOCK_REGEX.find(response)?.groupValues?.get(1)?.trim()
        if (!outputBlock.isNullOrBlank()) {
            return outputBlock
        }

        val markdownBlock = MARKDOWN_BLOCK_REGEX.find(response)?.groupValues?.get(1)?.trim()
        if (!markdownBlock.isNullOrBlank()) {
            return markdownBlock
        }

        val trimmed = response.trim()
        val parsedJson = runCatching { objectMapper.readTree(trimmed) }.getOrNull()
        if (parsedJson != null && parsedJson.isObject) {
            return ""
        }
        return trimmed
    }

    private fun buildDeterministicFallbackMarkdown(request: BriefingGenerationRequest): String {
        val personaSection = if (request.subagentOutputs.isEmpty()) {
            "No successful persona outputs were available for synthesis."
        } else {
            request.subagentOutputs.joinToString("\n\n") { output ->
                "### ${output.personaName}\n${output.curatedText.trim()}"
            }
        }

        return buildString {
            appendLine("## Briefing")
            appendLine()
            appendLine("Intent: ${request.enrichmentIntent}")
            appendLine()
            appendLine(personaSection)
        }.trim()
    }

    data class AiRunnerConfig(
        val provider: String = "google_genai",
        val model: String = "gemini-2.5-flash"
    )

    private data class StructuredSynthesisResponse(
        val title: String?,
        val markdownBody: String,
        val references: List<BriefingReferenceCandidate>,
        val conflictHighlights: List<BriefingConflictHighlightResponse>
    )

    companion object {
        private const val MAX_PERSONA_OUTPUT_CHARS = 5_000
        private val JSON_BLOCK_REGEX = Regex("```json\\s*\\n(.*?)\\n```", RegexOption.DOT_MATCHES_ALL)
        private val OUTPUT_BLOCK_REGEX = Regex("```output\\s*\\n(.*?)\\n```", RegexOption.DOT_MATCHES_ALL)
        private val MARKDOWN_BLOCK_REGEX = Regex("```markdown\\s*\\n(.*?)\\n```", RegexOption.DOT_MATCHES_ALL)
    }
}
