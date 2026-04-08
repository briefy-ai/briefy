package com.briefy.api.application.briefing.tool

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.assertj.core.api.Assertions.assertThat

class WebToolConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(WebToolConfiguration::class.java, TestConfig::class.java)

    @Test
    fun `creates web search tool when chat flag is enabled`() {
        contextRunner
            .withPropertyValues(
                "chat.conversation.tools.web-search.enabled=true",
                "briefing.execution.tools.web-search.enabled=false",
                "briefing.execution.tools.web-fetch.enabled=false",
                "briefing.execution.tools.source-lookup.enabled=false",
                "briefing.execution.tools.web-search.brave-api-key=test-key"
            )
            .run { context ->
                assertThat(context).hasSingleBean(WebSearchTool::class.java)
                assertThat(context).doesNotHaveBean(WebFetchTool::class.java)
            }
    }

    @Test
    fun `creates web fetch tool when chat flag is enabled`() {
        contextRunner
            .withPropertyValues(
                "chat.conversation.tools.web-fetch.enabled=true",
                "briefing.execution.tools.web-search.enabled=false",
                "briefing.execution.tools.web-fetch.enabled=false",
                "briefing.execution.tools.source-lookup.enabled=false"
            )
            .run { context ->
                assertThat(context).hasSingleBean(WebFetchTool::class.java)
                assertThat(context).doesNotHaveBean(WebSearchTool::class.java)
            }
    }

    @Test
    fun `does not create external web tools when chat and briefing flags are disabled`() {
        contextRunner
            .withPropertyValues(
                "chat.conversation.tools.web-search.enabled=false",
                "chat.conversation.tools.web-fetch.enabled=false",
                "briefing.execution.tools.web-search.enabled=false",
                "briefing.execution.tools.web-fetch.enabled=false",
                "briefing.execution.tools.source-lookup.enabled=false"
            )
            .run { context ->
                assertThat(context).doesNotHaveBean(WebSearchTool::class.java)
                assertThat(context).doesNotHaveBean(WebFetchTool::class.java)
            }
    }

    @Configuration
    class TestConfig {
        @Bean
        fun objectMapper(): ObjectMapper = jacksonObjectMapper()
    }
}
