package com.briefy.api.domain.knowledgegraph.briefing

enum class BriefingEnrichmentIntent {
    DEEP_DIVE,
    CONTEXTUAL_EXPANSION,
    TRUTH_GROUNDING;

    companion object {
        fun fromApiValue(raw: String): BriefingEnrichmentIntent {
            val normalized = raw.trim().uppercase().replace('-', '_').replace(' ', '_')
            return valueOf(normalized)
        }
    }
}
