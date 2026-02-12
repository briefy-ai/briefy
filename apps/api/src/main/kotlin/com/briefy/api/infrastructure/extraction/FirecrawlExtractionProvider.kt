package com.briefy.api.infrastructure.extraction

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.Instant

class FirecrawlExtractionProvider(
    private val restClient: RestClient,
    private val apiKey: String,
    private val waitForMs: Long
) : ExtractionProvider {
    override val id: ExtractionProviderId = ExtractionProviderId.FIRECRAWL

    override fun extract(url: String): ExtractionResult {
        val response = try {
            restClient.post()
                .uri("/v2/scrape")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $apiKey")
                .body(
                    FirecrawlScrapeRequest(
                        url = url,
                        formats = listOf("markdown"),
                        onlyMainContent = true,
                        waitFor = waitForMs
                    )
                )
                .retrieve()
                .onStatus(HttpStatusCode::isError) { _, clientResponse ->
                    throw ExtractionProviderException(
                        providerId = id,
                        reason = ExtractionFailureReason.NETWORK,
                        message = "Firecrawl returned status ${clientResponse.statusCode}"
                    )
                }
                .body(FirecrawlScrapeResponse::class.java)
        } catch (e: ExtractionProviderException) {
            throw e
        } catch (e: RestClientException) {
            throw ExtractionProviderException(
                providerId = id,
                reason = ExtractionFailureReason.NETWORK,
                message = "Firecrawl request failed",
                cause = e
            )
        }

        if (response?.success != true || response.data == null) {
            throw ExtractionProviderException(
                providerId = id,
                reason = ExtractionFailureReason.UNKNOWN,
                message = "Firecrawl returned an invalid payload"
            )
        }

        val markdown = response.data.markdown?.trim().orEmpty()
        if (markdown.isBlank()) {
            throw ExtractionProviderException(
                providerId = id,
                reason = ExtractionFailureReason.UNKNOWN,
                message = "Firecrawl returned empty markdown"
            )
        }

        return ExtractionResult(
            text = markdown,
            title = response.data.metadata?.title,
            author = null,
            publishedDate = response.data.metadata?.publishedTime?.let { parseInstantOrNull(it) },
            aiFormatted = false
        )
    }

    private fun parseInstantOrNull(value: String): Instant? {
        return try {
            Instant.parse(value)
        } catch (_: Exception) {
            null
        }
    }
}

private data class FirecrawlScrapeRequest(
    val url: String,
    val formats: List<String>,
    val onlyMainContent: Boolean,
    val waitFor: Long
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class FirecrawlScrapeResponse(
    val success: Boolean? = null,
    val data: FirecrawlScrapeData? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class FirecrawlScrapeData(
    val markdown: String? = null,
    val metadata: FirecrawlScrapeMetadata? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class FirecrawlScrapeMetadata(
    val title: String? = null,
    val publishedTime: String? = null
)
