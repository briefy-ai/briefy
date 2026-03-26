package com.briefy.api.infrastructure.extraction

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException

class SupadataYouTubeExtractionProvider(
    private val restClient: RestClient,
    private val apiKey: String,
    private val fallbackProvider: ExtractionProvider,
    private val timeoutMs: Int,
    private val oEmbedBaseUrl: String = DEFAULT_OEMBED_BASE_URL
) : ExtractionProvider {
    override val id: ExtractionProviderId = ExtractionProviderId.SUPADATA_YOUTUBE

    private val logger = LoggerFactory.getLogger(SupadataYouTubeExtractionProvider::class.java)

    init {
        require(timeoutMs > 0) { "timeoutMs must be greater than 0" }
    }

    override fun extract(url: String): ExtractionResult {
        val ref = YouTubeUrlParser.parse(url)
        val transcript = fetchTranscript(ref.canonicalUrl)
            ?.takeIf { !it.content.isNullOrBlank() }
            ?: return fallbackToYoutube(url, ref.videoId)

        val oEmbed = fetchOEmbed(ref.canonicalUrl)

        return ExtractionResult(
            text = transcript.content!!.trim(),
            title = oEmbed?.title,
            author = oEmbed?.authorName,
            publishedDate = null,
            ogImageUrl = oEmbed?.thumbnailUrl,
            aiFormatted = false,
            videoId = ref.videoId,
            videoEmbedUrl = ref.embedUrl,
            videoDurationSeconds = null,
            transcriptSource = "supadata",
            transcriptLanguage = transcript.lang
        )
    }

    private fun fetchTranscript(canonicalUrl: String): SupadataTranscriptResponse? {
        return try {
            val response = restClient.get()
                .uri { builder ->
                    builder.path("/v1/transcript")
                        .queryParam("url", canonicalUrl)
                        .queryParam("text", true)
                        .queryParam("mode", "native")
                        .queryParam("lang", "en")
                        .build()
                }
                .header("x-api-key", apiKey)
                .retrieve()
                .toEntity(SupadataTranscriptResponse::class.java)

            if (response.statusCode.value() == 206) {
                logger.info("[extractor:supadata] partial_transcript url={} status=206", canonicalUrl)
                return null
            }

            response.body
        } catch (e: RestClientResponseException) {
            when (e.statusCode.value()) {
                401 -> throw ExtractionProviderException(
                    providerId = id,
                    reason = ExtractionFailureReason.BLOCKED,
                    message = "supadata_invalid_api_key",
                    cause = e
                )

                429 -> throw ExtractionProviderException(
                    providerId = id,
                    reason = ExtractionFailureReason.BLOCKED,
                    message = "supadata_rate_limited",
                    cause = e
                )

                else -> {
                    logger.info(
                        "[extractor:supadata] transcript_request_failed status={} url={} falling_back=true",
                        e.statusCode.value(),
                        canonicalUrl
                    )
                    null
                }
            }
        } catch (e: RestClientException) {
            logger.info("[extractor:supadata] transcript_request_failed url={} falling_back=true", canonicalUrl, e)
            null
        }
    }

    private fun fetchOEmbed(canonicalUrl: String): YouTubeOEmbedResponse? {
        return try {
            restClient.get()
                .uri("${oEmbedBaseUrl.trimEnd('/')}/oembed?url={url}&format=json", canonicalUrl)
                .retrieve()
                .body(YouTubeOEmbedResponse::class.java)
        } catch (e: RestClientException) {
            logger.info("[extractor:supadata] oembed_request_failed url={}", canonicalUrl, e)
            null
        }
    }

    private fun fallbackToYoutube(url: String, videoId: String): ExtractionResult {
        logger.info("[extractor:supadata] falling_back_to_youtube_provider url={} videoId={}", url, videoId)
        return fallbackProvider.extract(url)
    }

    companion object {
        private const val DEFAULT_OEMBED_BASE_URL = "https://www.youtube.com"
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class SupadataTranscriptResponse(
    val content: String? = null,
    val lang: String? = null,
    val availableLangs: List<String>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class YouTubeOEmbedResponse(
    val title: String? = null,
    @param:JsonProperty("author_name")
    val authorName: String? = null,
    @param:JsonProperty("thumbnail_url")
    val thumbnailUrl: String? = null
)
