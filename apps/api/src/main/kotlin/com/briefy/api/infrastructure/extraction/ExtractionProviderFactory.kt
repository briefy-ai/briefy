package com.briefy.api.infrastructure.extraction

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class ExtractionProviderFactory(
    private val jsoupExtractionProvider: JsoupExtractionProvider,
    private val youTubeExtractionProvider: YouTubeExtractionProvider,
    private val objectMapper: ObjectMapper,
    @param:Value("\${extraction.firecrawl.base-url:https://api.firecrawl.dev}") private val firecrawlBaseUrl: String,
    @param:Value("\${extraction.firecrawl.wait-for-ms:1000}") private val firecrawlWaitForMs: Long,
    @param:Value("\${extraction.firecrawl.agent.model:spark-1-mini}") private val firecrawlAgentModel: String,
    @param:Value("\${extraction.firecrawl.agent.poll-interval-ms:500}") private val firecrawlAgentPollIntervalMs: Long,
    @param:Value("\${extraction.firecrawl.agent.max-wait-ms:45000}") private val firecrawlAgentMaxWaitMs: Long,
    @param:Value("\${extraction.firecrawl.agent.max-credits:0}") private val firecrawlAgentMaxCredits: Int,
    @param:Value("\${extraction.x-api.base-url:https://api.x.com}") private val xApiBaseUrl: String,
    @param:Value("\${extraction.x-api.timeout-ms:10000}") private val xApiTimeoutMs: Long,
    @param:Value("\${extraction.x-api.thread-max-results:100}") private val xApiThreadMaxResults: Int
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

    fun firecrawlAgent(apiKey: String): ExtractionProvider {
        val maxCredits = firecrawlAgentMaxCredits.takeIf { it > 0 }
        return FirecrawlAgentExtractionProvider(
            restClient = RestClient.builder()
                .baseUrl(firecrawlBaseUrl)
                .build(),
            objectMapper = objectMapper,
            apiKey = apiKey,
            model = firecrawlAgentModel,
            pollIntervalMs = firecrawlAgentPollIntervalMs,
            maxWaitMs = firecrawlAgentMaxWaitMs,
            maxCredits = maxCredits
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
