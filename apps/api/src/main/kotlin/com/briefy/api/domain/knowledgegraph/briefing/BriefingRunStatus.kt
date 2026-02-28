package com.briefy.api.domain.knowledgegraph.briefing

enum class BriefingRunStatus(val dbValue: String) {
    QUEUED("queued"),
    RUNNING("running"),
    CANCELLING("cancelling"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    CANCELLED("cancelled");

    fun isTerminal(): Boolean {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED
    }

    fun canTransitionTo(target: BriefingRunStatus): Boolean {
        return when (this) {
            QUEUED -> target == RUNNING
            RUNNING -> target == CANCELLING || target == FAILED || target == SUCCEEDED
            CANCELLING -> target == CANCELLED
            SUCCEEDED, FAILED, CANCELLED -> false
        }
    }

    companion object {
        fun fromDbValue(dbValue: String): BriefingRunStatus {
            return entries.firstOrNull { it.dbValue == dbValue }
                ?: throw IllegalArgumentException("Unknown BriefingRunStatus dbValue=$dbValue")
        }
    }
}
