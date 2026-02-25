package com.briefy.api.application.briefing

import com.briefy.api.domain.enrichment.AgentPersona
import com.briefy.api.domain.knowledgegraph.source.Source
import org.springframework.stereotype.Component

@Component
class DeterministicBriefingPlanGenerator : BriefingPlanGenerator {
    override fun generate(
        enrichmentIntent: String,
        sources: List<Source>,
        personas: List<AgentPersona>
    ): List<BriefingPlanDraft> {
        if (personas.isEmpty()) {
            return fallbackPlan(enrichmentIntent)
        }

        val sourceTitles = sources.mapNotNull { it.metadata?.title?.trim()?.takeIf(String::isNotBlank) }
        val sourceContext = if (sourceTitles.isEmpty()) {
            "the selected source set"
        } else {
            sourceTitles.joinToString(", ").take(240)
        }

        return personas.take(MAX_DETERMINISTIC_PLAN_STEPS).mapIndexed { index, persona ->
            BriefingPlanDraft(
                personaId = persona.id,
                personaName = persona.name,
                task = buildPersonaTask(
                    intent = enrichmentIntent,
                    personaName = persona.name,
                    sourceContext = sourceContext,
                    stepOrder = index + 1
                )
            )
        }
    }

    private fun buildPersonaTask(
        intent: String,
        personaName: String,
        sourceContext: String,
        stepOrder: Int
    ): String {
        return when (intent.trim().uppercase()) {
            "DEEP_DIVE" -> "Step $stepOrder: $personaName analyzes $sourceContext for nuanced concepts and missing details."
            "CONTEXTUAL_EXPANSION" -> "Step $stepOrder: $personaName expands $sourceContext with adjacent ideas and related context."
            "TRUTH_GROUNDING" -> "Step $stepOrder: $personaName challenges claims in $sourceContext and seeks opposing evidence."
            else -> "Step $stepOrder: $personaName analyzes $sourceContext for key insights."
        }
    }

    private fun fallbackPlan(intent: String): List<BriefingPlanDraft> {
        return when (intent.trim().uppercase()) {
            "TRUTH_GROUNDING" -> listOf(
                BriefingPlanDraft(
                    personaId = null,
                    personaName = "Claim Auditor",
                    task = "Audit the source claims and isolate statements that require evidence."
                ),
                BriefingPlanDraft(
                    personaId = null,
                    personaName = "Counterpoint Scout",
                    task = "Collect opposing arguments and identify where they diverge from the source."
                ),
                BriefingPlanDraft(
                    personaId = null,
                    personaName = "Synthesis Writer",
                    task = "Synthesize agreements and disagreements into a concise, citation-ready briefing."
                )
            )

            "CONTEXTUAL_EXPANSION" -> listOf(
                BriefingPlanDraft(
                    personaId = null,
                    personaName = "Context Mapper",
                    task = "Map adjacent ideas and related themes around the source material."
                ),
                BriefingPlanDraft(
                    personaId = null,
                    personaName = "Library Connector",
                    task = "Connect source material with relevant knowledge already in the user's library."
                ),
                BriefingPlanDraft(
                    personaId = null,
                    personaName = "Synthesis Writer",
                    task = "Produce a contextual expansion briefing with clear citations."
                )
            )

            else -> listOf(
                BriefingPlanDraft(
                    personaId = null,
                    personaName = "Deep Reader",
                    task = "Extract nuanced concepts, assumptions, and implications from the source material."
                ),
                BriefingPlanDraft(
                    personaId = null,
                    personaName = "Evidence Organizer",
                    task = "Organize supporting evidence and examples for the key findings."
                ),
                BriefingPlanDraft(
                    personaId = null,
                    personaName = "Synthesis Writer",
                    task = "Write a deep-dive briefing with concise narrative and citations."
                )
            )
        }
    }

    companion object {
        private const val MAX_DETERMINISTIC_PLAN_STEPS = 3
    }
}
