package com.briefy.api.application.briefing

import org.springframework.stereotype.Component

@Component
class LegacyEngineSynthesisExecutionRunner(
    private val briefingGenerationEngine: BriefingGenerationEngine
) : SynthesisExecutionRunner {
    override fun run(request: BriefingGenerationRequest): BriefingGenerationResult {
        return briefingGenerationEngine.generate(request)
    }
}
