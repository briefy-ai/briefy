package com.briefy.api.application.briefing

import com.briefy.api.domain.enrichment.AgentPersonaRepository
import com.briefy.api.domain.enrichment.AgentPersonaUseCase
import com.briefy.api.domain.knowledgegraph.source.Source
import com.briefy.api.infrastructure.ai.AiErrorCategory
import com.briefy.api.infrastructure.ai.setAttributeIfNotBlank
import com.briefy.api.infrastructure.ai.withSpan
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class BriefingPlannerService(
    private val agentPersonaRepository: AgentPersonaRepository,
    private val aiBriefingPlanGenerator: AiBriefingPlanGenerator,
    private val deterministicBriefingPlanGenerator: DeterministicBriefingPlanGenerator,
    private val tracer: Tracer,
    @param:Value("\${briefing.planning.enabled:true}")
    private val aiPlanningEnabled: Boolean
) {
    private val logger = LoggerFactory.getLogger(BriefingPlannerService::class.java)

    fun buildPlan(
        briefingId: UUID,
        userId: UUID,
        enrichmentIntent: String,
        sources: List<Source>
    ): List<BriefingPlanDraft> {
        return tracer.withSpan(
            name = "briefing.planning",
            noParent = true,
            configure = { span ->
                span.setAttribute("briefing.id", briefingId.toString())
                span.setAttribute("briefing.intent", enrichmentIntent)
                span.setAttribute("briefing.source_count", sources.size.toLong())
                span.setAttribute("langfuse.user.id", userId.toString())
            }
        ) { span ->
            val personas = agentPersonaRepository.findForUseCase(userId, AgentPersonaUseCase.ENRICHMENT)
            span.setAttribute("planning.persona_count", personas.size.toLong())

            if (personas.isEmpty()) {
                span.setAttribute("planning.strategy", "deterministic_no_personas")
                span.setStatus(StatusCode.OK)
                return@withSpan deterministicBriefingPlanGenerator.generate(
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
                span.setAttribute("planning.strategy", "deterministic_ai_disabled")
                span.setStatus(StatusCode.OK)
                return@withSpan deterministicBriefingPlanGenerator.generate(
                    enrichmentIntent = enrichmentIntent,
                    sources = sources,
                    personas = personas
                )
            }

            var planningStrategy = "ai"
            runCatching {
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
                planningStrategy = "deterministic_fallback_after_ai_error"
                span.recordException(error)
                span.setAttribute("planning.ai_fallback", true)
                span.setAttribute("planning.ai.error.category", AiErrorCategory.from(error).name.lowercase())
                span.setAttributeIfNotBlank("planning.ai.error.class", error.javaClass.simpleName)
            }.getOrElse {
                deterministicBriefingPlanGenerator.generate(
                    enrichmentIntent = enrichmentIntent,
                    sources = sources,
                    personas = personas
                )
            }.also {
                span.setAttribute("planning.strategy", planningStrategy)
                span.setStatus(StatusCode.OK)
            }
        }
    }
}
