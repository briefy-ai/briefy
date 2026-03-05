package com.briefy.api.application.briefing

class LegacyEngineSynthesisExecutionRunner(
    private val briefingGenerationEngine: BriefingGenerationEngine
) : SynthesisExecutionRunner {
    override fun run(request: BriefingGenerationRequest): BriefingGenerationResult {
        return briefingGenerationEngine.generate(request)
    }
}
