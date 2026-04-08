package com.briefy.api.application.briefing.tool

import com.briefy.api.application.enrichment.SourceSimilarityService
import com.briefy.api.domain.knowledgegraph.source.SourceRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Conditional
import org.springframework.context.annotation.ConfigurationCondition
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class WebToolConfiguration {

    @Bean
    @Conditional(WebSearchEnabledCondition::class)
    fun webSearchTool(
        objectMapper: ObjectMapper,
        @Value("\${briefing.execution.tools.web-search.brave-api-key:}") braveApiKey: String,
        @Value("\${briefing.execution.tools.web-search.brave-base-url:https://api.search.brave.com}") braveBaseUrl: String,
        @Value("\${briefing.execution.tools.web-search.timeout-ms:10000}") timeoutMs: Int
    ): WebSearchTool {
        require(braveApiKey.isNotBlank()) {
            "Brave API key must be set via BRAVE_SEARCH_API_KEY " +
                "(property: briefing.execution.tools.web-search.brave-api-key)"
        }
        return BraveWebSearchProvider(
            apiKey = braveApiKey,
            objectMapper = objectMapper,
            baseUrl = braveBaseUrl,
            timeoutMs = timeoutMs
        )
    }

    @Bean
    @Conditional(WebFetchEnabledCondition::class)
    fun webFetchTool(
        @Value("\${briefing.execution.tools.web-fetch.timeout-ms:15000}") timeoutMs: Int,
        @Value("\${briefing.execution.tools.web-fetch.max-body-bytes:512000}") maxBodyBytes: Int
    ): WebFetchTool {
        return HttpWebFetchProvider(
            timeoutMs = timeoutMs,
            maxBodyBytes = maxBodyBytes
        )
    }

    @Bean
    @ConditionalOnProperty("briefing.execution.tools.source-lookup.enabled", havingValue = "true")
    fun sourceLookupTool(
        sourceSimilarityService: SourceSimilarityService,
        sourceRepository: SourceRepository
    ): SourceLookupTool {
        return SourceLookupToolProvider(
            sourceSimilarityService = sourceSimilarityService,
            sourceRepository = sourceRepository
        )
    }

    private class WebSearchEnabledCondition : AnyNestedCondition(ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN) {
        @ConditionalOnProperty("briefing.execution.tools.web-search.enabled", havingValue = "true")
        class BriefingExecutionEnabled

        @ConditionalOnProperty("chat.conversation.tools.web-search.enabled", havingValue = "true")
        class ChatConversationEnabled
    }

    private class WebFetchEnabledCondition : AnyNestedCondition(ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN) {
        @ConditionalOnProperty("briefing.execution.tools.web-fetch.enabled", havingValue = "true")
        class BriefingExecutionEnabled

        @ConditionalOnProperty("chat.conversation.tools.web-fetch.enabled", havingValue = "true")
        class ChatConversationEnabled
    }
}
