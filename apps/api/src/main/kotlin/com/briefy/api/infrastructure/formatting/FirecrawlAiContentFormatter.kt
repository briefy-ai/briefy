package com.briefy.api.infrastructure.formatting

import com.briefy.api.infrastructure.ai.AiAdapter
import com.briefy.api.infrastructure.extraction.ExtractionProviderId
import org.springframework.stereotype.Component

@Component
class FirecrawlAiContentFormatter(
    private val aiAdapter: AiAdapter
) : ExtractionContentFormatter {
    override fun supports(extractorId: ExtractionProviderId): Boolean {
        return extractorId == ExtractionProviderId.FIRECRAWL
    }

    override fun format(extractedContent: String, provider: String, model: String): String {
        require(extractedContent.isNotBlank()) { "extractedContent must not be blank" }

        val systemPrompt = """
            the following is an article, new, blog or similar that has been extracted using firecrawl. your job is to:

            1. remove everything that doesn't belong to the main content (menu items, sidebars, footers, etc)
            2. infere a feasible markdown formatting given the plain text provided
            3. keep the content identical
            4. remove title and post metadata (publish date, author, etc)
        """.trimIndent()

        val userPrompt = """
            here's the content:
            $extractedContent
        """.trimIndent()

        return aiAdapter.complete(
            provider = provider,
            model = model,
            prompt = userPrompt,
            systemPrompt = systemPrompt,
            useCase = "source_formatting"
        )
    }
}
