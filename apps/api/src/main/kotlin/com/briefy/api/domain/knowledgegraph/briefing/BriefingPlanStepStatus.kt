package com.briefy.api.domain.knowledgegraph.briefing

enum class BriefingPlanStepStatus {
    PLANNED,
    RUNNING,
    SUCCEEDED,
    FAILED;

    fun canTransitionTo(target: BriefingPlanStepStatus): Boolean {
        return when (this) {
            PLANNED -> target == RUNNING
            RUNNING -> target == SUCCEEDED || target == FAILED
            SUCCEEDED -> false
            FAILED -> false
        }
    }
}
