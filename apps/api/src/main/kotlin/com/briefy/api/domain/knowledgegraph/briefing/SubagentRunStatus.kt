package com.briefy.api.domain.knowledgegraph.briefing

enum class SubagentRunStatus(val dbValue: String) {
    PENDING("pending"),
    RUNNING("running"),
    RETRY_WAIT("retry_wait"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    SKIPPED("skipped"),
    SKIPPED_NO_OUTPUT("skipped_no_output"),
    CANCELLED("cancelled");

    fun isTerminal(): Boolean {
        return this == SUCCEEDED ||
            this == FAILED ||
            this == SKIPPED ||
            this == SKIPPED_NO_OUTPUT ||
            this == CANCELLED
    }

    fun canTransitionTo(target: SubagentRunStatus): Boolean {
        return when (this) {
            PENDING -> target == RUNNING || target == CANCELLED
            RUNNING -> target == RETRY_WAIT ||
                target == SUCCEEDED ||
                target == SKIPPED_NO_OUTPUT ||
                target == SKIPPED ||
                target == FAILED ||
                target == CANCELLED
            RETRY_WAIT -> target == RUNNING || target == CANCELLED
            SUCCEEDED, FAILED, SKIPPED, SKIPPED_NO_OUTPUT, CANCELLED -> false
        }
    }

    companion object {
        fun fromDbValue(dbValue: String): SubagentRunStatus {
            return entries.firstOrNull { it.dbValue == dbValue }
                ?: throw IllegalArgumentException("Unknown SubagentRunStatus dbValue=$dbValue")
        }
    }
}
