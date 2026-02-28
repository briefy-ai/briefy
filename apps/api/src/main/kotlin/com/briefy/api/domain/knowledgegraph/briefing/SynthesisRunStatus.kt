package com.briefy.api.domain.knowledgegraph.briefing

enum class SynthesisRunStatus(val dbValue: String) {
    NOT_STARTED("not_started"),
    RUNNING("running"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    SKIPPED("skipped"),
    CANCELLED("cancelled");

    fun isTerminal(): Boolean {
        return this == SUCCEEDED || this == FAILED || this == SKIPPED || this == CANCELLED
    }

    fun canTransitionTo(target: SynthesisRunStatus): Boolean {
        return when (this) {
            NOT_STARTED -> target == RUNNING || target == SKIPPED || target == CANCELLED
            RUNNING -> target == SUCCEEDED || target == FAILED || target == CANCELLED
            SUCCEEDED, FAILED, SKIPPED, CANCELLED -> false
        }
    }

    companion object {
        fun fromDbValue(dbValue: String): SynthesisRunStatus {
            return entries.firstOrNull { it.dbValue == dbValue }
                ?: throw IllegalArgumentException("Unknown SynthesisRunStatus dbValue=$dbValue")
        }
    }
}
