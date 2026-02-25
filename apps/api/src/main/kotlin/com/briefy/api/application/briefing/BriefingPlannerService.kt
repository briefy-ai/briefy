package com.briefy.api.application.briefing

import com.briefy.api.domain.enrichment.AgentPersonaRepository
import com.briefy.api.domain.enrichment.AgentPersonaUseCase
import com.briefy.api.domain.knowledgegraph.source.Source
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class BriefingPlannerService(
    private val agentPersonaRepository: AgentPersonaRepository,
    private val aiBriefingPlanGenerator: AiBriefingPlanGenerator,
    private val deterministicBriefingPlanGenerator: DeterministicBriefingPlanGenerator,
    @param:Value("\${briefing.planning.enabled:true}")
    private val aiPlanningEnabled: Boolean
) {
    private val logger = LoggerFactory.getLogger(BriefingPlannerService::class.java)

    fun buildPlan(
        userId: UUID,
        enrichmentIntent: String,
        sources: List<Source>
    ): List<BriefingPlanDraft> {
        val personas = agentPersonaRepository.findForUseCase(userId, AgentPersonaUseCase.ENRICHMENT)
        if (personas.isEmpty()) {
            return deterministicBriefingPlanGenerator.generate(
                enrichmentIntent = enrichmentIntent,
                sources = sources,
                personas = emptyList()
            )
        }

        if (!aiPlanningEnabled) {
            logger.info(
                "[briefing-planner] ai_planning_disabled userId={} intent={} sourceCount={}",
                userId,
                enrichmentIntent,
                sources.size
            )
            return deterministicBriefingPlanGenerator.generate(
                enrichmentIntent = enrichmentIntent,
                sources = sources,
                personas = personas
            )
        }

        return runCatching {
            aiBriefingPlanGenerator.generate(
                enrichmentIntent = enrichmentIntent,
                sources = sources,
                personas = personas
            )
        }.onFailure { error ->
            logger.warn(
                "[briefing-planner] ai_planning_failed userId={} intent={} sourceCount={} reason={}",
                userId,
                enrichmentIntent,
                sources.size,
                error.javaClass.simpleName
            )
        }.getOrElse {
            deterministicBriefingPlanGenerator.generate(
                enrichmentIntent = enrichmentIntent,
                sources = sources,
                personas = personas
            )
        }
    }
}
