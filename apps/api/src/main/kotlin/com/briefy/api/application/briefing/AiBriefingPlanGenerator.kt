package com.briefy.api.application.briefing

import com.briefy.api.domain.enrichment.AgentPersona
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.infrastructure.ai.AiAdapter
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class AiBriefingPlanGenerator(
    private val aiAdapter: AiAdapter,
    private val objectMapper: ObjectMapper,
    @param:Value("\${briefing.planning.provider:google_genai}")
    private val provider: String,
    @param:Value("\${briefing.planning.model:gemini-2.5-flash}")
    private val model: String
) : BriefingPlanGenerator {
    override fun generate(
        enrichmentIntent: String,
        sources: List<Source>,
        personas: List<AgentPersona>
    ): List<BriefingPlanDraft> {
        require(sources.isNotEmpty()) { "sources must not be empty" }
        require(personas.isNotEmpty()) { "personas must not be empty" }

        val response = aiAdapter.complete(
            provider = provider,
            model = model,
            prompt = buildPrompt(enrichmentIntent, sources, personas),
            systemPrompt = SYSTEM_PROMPT,
            useCase = "briefing_planning"
        )

        return parseAndValidate(response, personas)
    }

    private fun buildPrompt(
        enrichmentIntent: String,
        sources: List<Source>,
        personas: List<AgentPersona>
    ): String {
        val personaCatalog = personas.joinToString("\n") { persona ->
            "- ${persona.name}: role=${persona.role.take(200)} | purpose=${persona.purpose.take(200)}"
        }

        val sourceDetails = sources.joinToString("\n\n") { source ->
            val title = source.metadata?.title ?: source.url.normalized
            buildString {
                appendLine("sourceId: ${source.id}")
                appendLine("title: $title")
                appendLine("url: ${source.url.normalized}")
                appendLine("content:")
                appendLine(source.content?.text.orEmpty())
            }.trim()
        }

        return buildString {
            appendLine("Create a briefing plan for enrichment intent: $enrichmentIntent.")
            appendLine()
            appendLine("Output must be valid JSON only. No prose, no markdown, no code fences.")
            appendLine("Schema:")
            appendLine("""{"steps":[{"personaName":"string","task":"string"}]}""")
            appendLine()
            appendLine("Rules:")
            appendLine("- Return between 2 and 5 steps.")
            appendLine("- Keep steps concise and actionable.")
            appendLine("- Prefer persona names from the provided persona catalog.")
            appendLine("- Do not include extra keys.")
            appendLine()
            appendLine("Persona catalog:")
            appendLine(personaCatalog)
            appendLine()
            appendLine("Sources:")
            appendLine(sourceDetails)
        }
    }

    private fun parseAndValidate(
        rawOutput: String,
        personas: List<AgentPersona>
    ): List<BriefingPlanDraft> {
        val root = objectMapper.readTree(extractJsonObject(rawOutput))
        val stepsNode = root.get("steps")
            ?: throw IllegalStateException("Planner response missing 'steps'")
        if (!stepsNode.isArray) {
            throw IllegalStateException("Planner response field 'steps' must be an array")
        }

        val personasByName = personas.associateBy { normalizePersonaName(it.name) }
        val dedupedNames = mutableSetOf<String>()
        val steps = mutableListOf<BriefingPlanDraft>()

        stepsNode.forEach { stepNode ->
            if (steps.size >= MAX_STEPS) {
                return@forEach
            }

            val personaName = stepNode.path("personaName").asText("").trim()
            val task = stepNode.path("task").asText("").trim()
            if (personaName.isBlank() || task.length < MIN_TASK_CHARS) {
                return@forEach
            }

            val normalizedName = normalizePersonaName(personaName)
            if (!dedupedNames.add(normalizedName)) {
                return@forEach
            }

            steps.add(
                BriefingPlanDraft(
                    personaId = personasByName[normalizedName]?.id,
                    personaName = personaName.take(MAX_PERSONA_NAME_CHARS),
                    task = task.take(MAX_TASK_CHARS)
                )
            )
        }

        if (steps.size < MIN_STEPS) {
            throw IllegalStateException("Planner returned ${steps.size} valid steps; minimum is $MIN_STEPS")
        }

        return steps
    }

    private fun extractJsonObject(text: String): String {
        val fenced = FENCED_JSON_REGEX.find(text)?.groupValues?.getOrNull(1)
        if (!fenced.isNullOrBlank()) {
            return fenced
        }

        val firstBrace = text.indexOf('{')
        val lastBrace = text.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return text.substring(firstBrace, lastBrace + 1)
        }
        return text
    }

    private fun normalizePersonaName(name: String): String = name.trim().lowercase()

    companion object {
        private const val MIN_STEPS = 2
        private const val MAX_STEPS = 5
        private const val MIN_TASK_CHARS = 20
        private const val MAX_TASK_CHARS = 600
        private const val MAX_PERSONA_NAME_CHARS = 120
        private val FENCED_JSON_REGEX = Regex("```(?:json)?\\s*(\\{[\\s\\S]*\\})\\s*```", RegexOption.IGNORE_CASE)
        private const val SYSTEM_PROMPT = """
You are a planning engine for briefing generation.
Return strict JSON only with this shape:
{"steps":[{"personaName":"string","task":"string"}]}
No markdown, no commentary.
"""
    }
}
