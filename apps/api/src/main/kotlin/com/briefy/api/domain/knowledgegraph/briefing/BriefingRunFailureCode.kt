package com.briefy.api.domain.knowledgegraph.briefing

enum class BriefingRunFailureCode(val dbValue: String) {
    GLOBAL_TIMEOUT("global_timeout"),
    SYNTHESIS_GATE_NOT_MET("synthesis_gate_not_met"),
    SYNTHESIS_FAILED("synthesis_failed"),
    ORCHESTRATION_ERROR("orchestration_error"),
    CANCELLED("cancelled");

    companion object {
        fun fromDbValue(dbValue: String): BriefingRunFailureCode {
            return entries.firstOrNull { it.dbValue == dbValue }
                ?: throw IllegalArgumentException("Unknown BriefingRunFailureCode dbValue=$dbValue")
        }
    }
}
