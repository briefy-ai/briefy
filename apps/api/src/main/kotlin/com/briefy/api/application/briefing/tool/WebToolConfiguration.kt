package com.briefy.api.application.briefing.tool

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class WebToolConfiguration {

    @Bean
    @ConditionalOnProperty("briefing.execution.tools.web-search.enabled", havingValue = "true")
    fun webSearchTool(
        objectMapper: ObjectMapper,
        @Value("\${briefing.execution.tools.web-search.brave-api-key:}") braveApiKey: String,
        @Value("\${briefing.execution.tools.web-search.brave-base-url:https://api.search.brave.com}") braveBaseUrl: String,
        @Value("\${briefing.execution.tools.web-search.timeout-ms:10000}") timeoutMs: Int
    ): WebSearchTool {
        require(braveApiKey.isNotBlank()) { "briefing.execution.tools.web-search.brave-api-key must be set" }
        return BraveWebSearchProvider(
            apiKey = braveApiKey,
            objectMapper = objectMapper,
            baseUrl = braveBaseUrl,
            timeoutMs = timeoutMs
        )
    }

    @Bean
    @ConditionalOnProperty("briefing.execution.tools.web-fetch.enabled", havingValue = "true")
    fun webFetchTool(
        @Value("\${briefing.execution.tools.web-fetch.timeout-ms:15000}") timeoutMs: Int,
        @Value("\${briefing.execution.tools.web-fetch.max-body-bytes:512000}") maxBodyBytes: Int
    ): WebFetchTool {
        return HttpWebFetchProvider(
            timeoutMs = timeoutMs,
            maxBodyBytes = maxBodyBytes
        )
    }
}
