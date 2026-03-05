package com.briefy.api.application.briefing

import com.briefy.api.infrastructure.ai.AiAdapter
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class SubagentRunnerConfigurationTest {

    private val configuration = SubagentRunnerConfiguration()

    @Test
    fun `returns AI synthesis runner when synthesis mode is ai`() {
        val runner = configuration.synthesisExecutionRunner(
            executionConfig = ExecutionConfigProperties(
                synthesis = ExecutionConfigProperties.SynthesisType.AI
            ),
            objectMapper = ObjectMapper(),
            aiAdapter = mock<AiAdapter>(),
            briefingGenerationEngine = DeterministicBriefingGenerationEngine()
        )

        assertTrue(runner is AiSynthesisExecutionRunner)
    }

    @Test
    fun `returns legacy synthesis runner when synthesis mode is legacy`() {
        val runner = configuration.synthesisExecutionRunner(
            executionConfig = ExecutionConfigProperties(
                synthesis = ExecutionConfigProperties.SynthesisType.LEGACY
            ),
            objectMapper = ObjectMapper(),
            aiAdapter = mock<AiAdapter>(),
            briefingGenerationEngine = DeterministicBriefingGenerationEngine()
        )

        assertTrue(runner is LegacyEngineSynthesisExecutionRunner)
    }
}
