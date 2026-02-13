package com.briefy.api.infrastructure.extraction

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class ExtractionProviderFactory(
    private val jsoupExtractionProvider: JsoupExtractionProvider,
    @param:Value("\${extraction.firecrawl.base-url:https://api.firecrawl.dev}") private val firecrawlBaseUrl: String,
    @param:Value("\${extraction.firecrawl.wait-for-ms:1000}") private val firecrawlWaitForMs: Long,
    @param:Value("\${extraction.x-api.base-url:https://api.x.com}") private val xApiBaseUrl: String,
    @param:Value("\${extraction.x-api.timeout-ms:10000}") private val xApiTimeoutMs: Long,
    @param:Value("\${extraction.x-api.thread-max-results:100}") private val xApiThreadMaxResults: Int
) {
    fun jsoup(): ExtractionProvider = jsoupExtractionProvider

    fun firecrawl(apiKey: String): ExtractionProvider {
        return FirecrawlExtractionProvider(
            restClient = RestClient.builder()
                .baseUrl(firecrawlBaseUrl)
                .build(),
            apiKey = apiKey,
            waitForMs = firecrawlWaitForMs
        )
    }

    fun xApi(bearerToken: String): ExtractionProvider {
        return XApiExtractionProvider(
            restClient = RestClient.builder()
                .baseUrl(xApiBaseUrl)
                .build(),
            bearerToken = bearerToken,
            timeoutMs = xApiTimeoutMs,
            threadMaxResults = xApiThreadMaxResults
        )
    }
}
