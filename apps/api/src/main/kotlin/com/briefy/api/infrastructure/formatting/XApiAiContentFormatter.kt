package com.briefy.api.infrastructure.formatting

import com.briefy.api.infrastructure.ai.AiAdapter
import com.briefy.api.infrastructure.extraction.ExtractionProviderId
import org.springframework.stereotype.Component

@Component
class XApiAiContentFormatter(
    private val aiAdapter: AiAdapter
) : ExtractionContentFormatter {
    override fun supports(extractorId: ExtractionProviderId): Boolean {
        return extractorId == ExtractionProviderId.X_API
    }

    override fun format(extractedContent: String, provider: String, model: String): String {
        require(extractedContent.isNotBlank()) { "extractedContent must not be blank" }

        val systemPrompt = """
            the following content has been extracted from x (twitter). your job is to:

            1. format the content as clean markdown
            2. keep the content identical
            3. do not remove any text
        """.trimIndent()

        val userPrompt = """
            here's the content:
            $extractedContent
        """.trimIndent()

        return aiAdapter.complete(
            provider = provider,
            model = model,
            prompt = userPrompt,
            systemPrompt = systemPrompt
        )
    }
}
