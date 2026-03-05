package com.briefy.api.application.briefing

import com.briefy.api.application.briefing.tool.WebFetchTool
import com.briefy.api.application.briefing.tool.WebSearchTool
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
        webFetchTool: WebFetchTool?
    ): SubagentExecutionRunner {
        return when (executionConfig.runner) {
            ExecutionConfigProperties.RunnerType.AI -> {
                logger.info("[runner-config] Using AI subagent execution runner (provider={}, model={})",
                    executionConfig.ai.provider, executionConfig.ai.model)
                AiSubagentExecutionRunner(
                    aiAdapter = aiAdapter,
                    webSearchTool = webSearchTool,
                    webFetchTool = webFetchTool,
                    objectMapper = objectMapper,
                    config = AiSubagentExecutionRunner.AiRunnerConfig(
                        provider = executionConfig.ai.provider,
                        model = executionConfig.ai.model,
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
                    executionConfig.ai.provider,
                    executionConfig.ai.model
                )
                AiSynthesisExecutionRunner(
                    aiAdapter = aiAdapter,
                    objectMapper = objectMapper,
                    config = AiSynthesisExecutionRunner.AiRunnerConfig(
                        provider = executionConfig.ai.provider,
                        model = executionConfig.ai.model
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
