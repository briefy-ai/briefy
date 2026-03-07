package com.briefy.api.application.briefing

import com.briefy.api.application.briefing.tool.WebFetchTool
import com.briefy.api.application.briefing.tool.WebSearchTool
import com.briefy.api.application.source.SourceSearch
import com.briefy.api.infrastructure.ai.AiAdapter
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SubagentRunnerConfiguration {

    private val logger = LoggerFactory.getLogger(SubagentRunnerConfiguration::class.java)

    @Bean
    fun subagentExecutionRunner(
        executionConfig: ExecutionConfigProperties,
        objectMapper: ObjectMapper,
        aiAdapter: AiAdapter,
        webSearchTool: WebSearchTool?,
        webFetchTool: WebFetchTool?,
        sourceSearch: SourceSearch
    ): SubagentExecutionRunner {
        return when (executionConfig.runner) {
            ExecutionConfigProperties.RunnerType.AI -> {
                logger.info("[runner-config] Using AI subagent execution runner (provider={}, model={})",
                    executionConfig.ai.subagent.provider, executionConfig.ai.subagent.model)
                AiSubagentExecutionRunner(
                    aiAdapter = aiAdapter,
                    webSearchTool = webSearchTool,
                    webFetchTool = webFetchTool,
                    sourceSearch = sourceSearch,
                    objectMapper = objectMapper,
                    config = AiSubagentExecutionRunner.AiRunnerConfig(
                        provider = executionConfig.ai.subagent.provider,
                        model = executionConfig.ai.subagent.model,
                        maxToolCalls = executionConfig.ai.maxToolCalls
                    )
                )
            }
            ExecutionConfigProperties.RunnerType.DETERMINISTIC -> {
                logger.info("[runner-config] Using deterministic subagent execution runner")
                DeterministicSequentialSubagentExecutionRunner(objectMapper)
            }
        }
    }

    @Bean
    fun synthesisExecutionRunner(
        executionConfig: ExecutionConfigProperties,
        objectMapper: ObjectMapper,
        aiAdapter: AiAdapter,
        briefingGenerationEngine: BriefingGenerationEngine
    ): SynthesisExecutionRunner {
        val legacyRunner = LegacyEngineSynthesisExecutionRunner(briefingGenerationEngine)
        return when (executionConfig.synthesis) {
            ExecutionConfigProperties.SynthesisType.AI -> {
                logger.info(
                    "[runner-config] Using AI synthesis execution runner (provider={}, model={})",
                    executionConfig.ai.synthesis.provider,
                    executionConfig.ai.synthesis.model
                )
                AiSynthesisExecutionRunner(
                    aiAdapter = aiAdapter,
                    objectMapper = objectMapper,
                    config = AiSynthesisExecutionRunner.AiRunnerConfig(
                        provider = executionConfig.ai.synthesis.provider,
                        model = executionConfig.ai.synthesis.model
                    )
                )
            }

            ExecutionConfigProperties.SynthesisType.LEGACY -> {
                logger.info("[runner-config] Using legacy synthesis execution runner")
                legacyRunner
            }
        }
    }
}
