package com.briefy.api.domain.knowledgegraph.briefing

enum class BriefingReferenceStatus {
    ACTIVE,
    PROMOTED;

    fun canTransitionTo(target: BriefingReferenceStatus): Boolean {
        return when (this) {
            ACTIVE -> target == PROMOTED
            PROMOTED -> false
        }
    }
}
