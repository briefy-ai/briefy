package com.briefy.api.application.briefing

interface BriefingGenerationEngine {
    fun generate(request: BriefingGenerationRequest): BriefingGenerationResult
}
