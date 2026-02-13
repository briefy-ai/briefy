package com.briefy.api.infrastructure.extraction

interface ExtractionProvider {
    val id: ExtractionProviderId
    fun extract(url: String): ExtractionResult
}

enum class ExtractionProviderId {
    FIRECRAWL,
    X_API,
    JSOUP
}

enum class ExtractionFailureReason {
    TIMEOUT,
    BLOCKED,
    UNSUPPORTED,
    NETWORK,
    UNKNOWN
}

class ExtractionProviderException(
    val providerId: ExtractionProviderId,
    val reason: ExtractionFailureReason,
    override val message: String,
    override val cause: Throwable? = null
) : RuntimeException(message, cause)

class AllExtractionProvidersFailedException(
    val url: String,
    val attempts: List<Pair<ExtractionProviderId, String>>
) : RuntimeException("All extraction providers failed for URL: $url")
