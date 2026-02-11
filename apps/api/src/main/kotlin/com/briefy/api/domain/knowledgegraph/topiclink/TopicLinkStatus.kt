package com.briefy.api.domain.knowledgegraph.topiclink

enum class TopicLinkStatus {
    SUGGESTED,
    ACTIVE,
    REMOVED;

    fun canTransitionTo(target: TopicLinkStatus): Boolean {
        return when (this) {
            SUGGESTED -> target == ACTIVE || target == REMOVED
            ACTIVE -> target == REMOVED
            REMOVED -> false
        }
    }
}
