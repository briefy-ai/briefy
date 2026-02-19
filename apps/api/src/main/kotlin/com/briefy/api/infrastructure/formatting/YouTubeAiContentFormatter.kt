package com.briefy.api.infrastructure.formatting

import com.briefy.api.infrastructure.ai.AiAdapter
import com.briefy.api.infrastructure.extraction.ExtractionProviderId
import org.springframework.stereotype.Component

@Component
class YouTubeAiContentFormatter(
    private val aiAdapter: AiAdapter
) : ExtractionContentFormatter {
    override fun supports(extractorId: ExtractionProviderId): Boolean {
        return extractorId == ExtractionProviderId.YOUTUBE
    }

    override fun format(extractedContent: String, provider: String, model: String): String {
        require(extractedContent.isNotBlank()) { "extractedContent must not be blank" }

        val prompt = """
            format the following captions into text in a way that is easily readable.
            use paragraph and line breaks when appropriate.
            keep the content unchanged.

            captions:
            $extractedContent
        """.trimIndent()

        return aiAdapter.complete(
            provider = provider,
            model = model,
            prompt = prompt,
            systemPrompt = null,
            useCase = "source_formatting"
        )
    }
}
