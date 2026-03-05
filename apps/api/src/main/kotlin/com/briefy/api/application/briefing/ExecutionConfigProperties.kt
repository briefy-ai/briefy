package com.briefy.api.application.briefing

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "briefing.execution")
data class ExecutionConfigProperties(
    val globalTimeoutSeconds: Long = 180L,
    val subagentTimeoutSeconds: Long = 90L,
    val maxAttempts: Int = 3,
    val runner: RunnerType = RunnerType.DETERMINISTIC,
    val synthesis: SynthesisType = SynthesisType.AI,
    val retry: RetryConfig = RetryConfig(),
    val ai: AiRunnerProperties = AiRunnerProperties()
) {
    enum class RunnerType { DETERMINISTIC, AI }
    enum class SynthesisType { LEGACY, AI }

    data class RetryConfig(
        val transientDelayFirstSeconds: Long = 1L,
        val transientDelaySecondSeconds: Long = 2L,
        val http429FallbackFirstSeconds: Long = 2L,
        val http429FallbackSecondSeconds: Long = 4L
    )

    data class AiRunnerProperties(
        val provider: String = "google_genai",
        val model: String = "gemini-2.5-flash",
        val maxToolCalls: Int = 8
    )
}
