package com.briefy.api.domain.knowledgegraph.topic

enum class TopicStatus {
    SUGGESTED,
    ACTIVE,
    ARCHIVED;

    fun canTransitionTo(target: TopicStatus): Boolean {
        return when (this) {
            SUGGESTED -> target == ACTIVE || target == ARCHIVED
            ACTIVE -> target == ARCHIVED || target == SUGGESTED
            ARCHIVED -> target == SUGGESTED || target == ACTIVE
        }
    }
}
