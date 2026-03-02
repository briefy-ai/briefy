package com.briefy.api.application.briefing

interface SynthesisExecutionRunner {
    fun run(request: BriefingGenerationRequest): BriefingGenerationResult
}
