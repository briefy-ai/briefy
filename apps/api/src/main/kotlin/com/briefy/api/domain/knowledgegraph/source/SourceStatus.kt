package com.briefy.api.domain.knowledgegraph.source

enum class SourceStatus {
    SUBMITTED,
    EXTRACTING,
    ACTIVE,
    FAILED,
    ARCHIVED;

    fun canTransitionTo(target: SourceStatus): Boolean {
        return when (this) {
            SUBMITTED -> target == EXTRACTING
            EXTRACTING -> target == ACTIVE || target == FAILED
            ACTIVE -> target == ARCHIVED
            FAILED -> target == SUBMITTED // retry
            ARCHIVED -> false
        }
    }
}
