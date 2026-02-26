package com.briefy.api.application.briefing

import com.briefy.api.domain.enrichment.AgentPersona
import com.briefy.api.domain.knowledgegraph.source.Source

interface BriefingPlanGenerator {
    fun generate(
        enrichmentIntent: String,
        sources: List<Source>,
        personas: List<AgentPersona>
    ): List<BriefingPlanDraft>
}
