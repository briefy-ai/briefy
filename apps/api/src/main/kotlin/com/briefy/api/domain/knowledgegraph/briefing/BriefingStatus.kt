package com.briefy.api.domain.knowledgegraph.briefing

enum class BriefingStatus {
    PLAN_PENDING_APPROVAL,
    APPROVED,
    GENERATING,
    READY,
    FAILED;

    fun canTransitionTo(target: BriefingStatus): Boolean {
        return when (this) {
            PLAN_PENDING_APPROVAL -> target == APPROVED
            APPROVED -> target == GENERATING
            GENERATING -> target == READY || target == FAILED
            READY -> false
            FAILED -> target == PLAN_PENDING_APPROVAL
        }
    }
}
