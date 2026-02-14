package com.briefy.api.infrastructure.extraction

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class ExtractionProviderFactory(
    private val jsoupExtractionProvider: JsoupExtractionProvider,
    private val youTubeExtractionProvider: YouTubeExtractionProvider,
    @param:Value("\${extraction.firecrawl.base-url:https://api.firecrawl.dev}") private val firecrawlBaseUrl: String,
    @param:Value("\${extraction.firecrawl.wait-for-ms:1000}") private val firecrawlWaitForMs: Long
) {
    fun jsoup(): ExtractionProvider = jsoupExtractionProvider
    fun youtube(): ExtractionProvider = youTubeExtractionProvider

    fun firecrawl(apiKey: String): ExtractionProvider {
        return FirecrawlExtractionProvider(
            restClient = RestClient.builder()
                .baseUrl(firecrawlBaseUrl)
                .build(),
            apiKey = apiKey,
            waitForMs = firecrawlWaitForMs
        )
    }
}
