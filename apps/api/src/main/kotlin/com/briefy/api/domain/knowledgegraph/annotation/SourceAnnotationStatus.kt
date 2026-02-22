package com.briefy.api.domain.knowledgegraph.annotation

enum class SourceAnnotationStatus {
    ACTIVE,
    ARCHIVED;

    fun canTransitionTo(target: SourceAnnotationStatus): Boolean {
        return when (this) {
            ACTIVE -> target == ARCHIVED
            ARCHIVED -> target == ACTIVE
        }
    }
}
