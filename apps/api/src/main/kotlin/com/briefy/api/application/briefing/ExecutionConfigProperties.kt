package com.briefy.api.application.briefing

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "briefing.execution")
data class ExecutionConfigProperties(
    val globalTimeoutSeconds: Long = 180L,
    val subagentTimeoutSeconds: Long = 90L,
    val maxAttempts: Int = 3,
    val retry: RetryConfig = RetryConfig()
) {
    data class RetryConfig(
        val transientDelayFirstSeconds: Long = 1L,
        val transientDelaySecondSeconds: Long = 2L,
        val http429FallbackFirstSeconds: Long = 2L,
        val http429FallbackSecondSeconds: Long = 4L
    )
}
