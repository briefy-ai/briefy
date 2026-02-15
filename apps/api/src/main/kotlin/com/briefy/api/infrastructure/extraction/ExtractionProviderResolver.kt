package com.briefy.api.infrastructure.extraction

import com.briefy.api.application.settings.UserSettingsService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ExtractionProviderResolver(
    private val extractionProviderFactory: ExtractionProviderFactory,
    private val userSettingsService: UserSettingsService
) {
    private val logger = LoggerFactory.getLogger(ExtractionProviderResolver::class.java)

    fun resolveProvider(userId: UUID, platform: String): ExtractionProvider {
        if (platform.equals("youtube", ignoreCase = true)) {
            logger.info(
                "[resolver] Extraction provider resolved userId={} platform={} provider={} reason=platform_youtube",
                userId,
                platform,
                ExtractionProviderId.YOUTUBE
            )
            return extractionProviderFactory.youtube()
        }

        if (isXPlatform(platform)) {
            return resolveForXPlatform(userId, platform)
        }

        if (!shouldTryFirecrawl(platform)) {
            logger.info(
                "[resolver] Extraction provider resolved userId={} platform={} provider={} reason=platform_excluded",
                userId,
                platform,
                ExtractionProviderId.JSOUP
            )
            return extractionProviderFactory.jsoup()
        }

        if (!userSettingsService.isFirecrawlEnabled(userId)) {
            logger.info(
                "[resolver] Extraction provider resolved userId={} platform={} provider={} reason=firecrawl_disabled_or_unconfigured",
                userId,
                platform,
                ExtractionProviderId.JSOUP
            )
            return extractionProviderFactory.jsoup()
        }

        val apiKey = userSettingsService.getFirecrawlApiKey(userId)
        if (!apiKey.isNullOrBlank()) {
            logger.info(
                "[resolver] Extraction provider resolved userId={} platform={} provider={} reason=firecrawl_enabled",
                userId,
                platform,
                ExtractionProviderId.FIRECRAWL
            )
            return extractionProviderFactory.firecrawl(apiKey)
        }

        logger.info(
            "[resolver] Extraction provider resolved userId={} platform={} provider={} reason=missing_firecrawl_key",
            userId,
            platform,
            ExtractionProviderId.JSOUP
        )
        return extractionProviderFactory.jsoup()
    }

    private fun resolveForXPlatform(userId: UUID, platform: String): ExtractionProvider {
        if (!userSettingsService.isXApiEnabled(userId)) {
            logger.info(
                "[resolver] Extraction provider resolved userId={} platform={} provider={} reason=x_api_disabled_or_unconfigured",
                userId,
                platform,
                ExtractionProviderId.JSOUP
            )
            return extractionProviderFactory.jsoup()
        }

        val bearerToken = userSettingsService.getXApiBearerToken(userId)
        if (!bearerToken.isNullOrBlank()) {
            logger.info(
                "[resolver] Extraction provider resolved userId={} platform={} provider={} reason=x_api_enabled",
                userId,
                platform,
                ExtractionProviderId.X_API
            )
            return extractionProviderFactory.xApi(bearerToken)
        }

        logger.info(
            "[resolver] Extraction provider resolved userId={} platform={} provider={} reason=missing_x_api_token",
            userId,
            platform,
            ExtractionProviderId.JSOUP
        )
        return extractionProviderFactory.jsoup()
    }

    private fun shouldTryFirecrawl(platform: String): Boolean {
        return platform.lowercase() !in excludedPlatforms
    }

    private fun isXPlatform(platform: String): Boolean {
        return platform.lowercase() in xPlatforms
    }

    companion object {
        private val excludedPlatforms = setOf("twitter", "x")
        private val xPlatforms = setOf("twitter", "x")
    }
}
