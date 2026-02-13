package com.briefy.api.application.settings

import com.briefy.api.domain.identity.settings.UserExtractionSettings
import com.briefy.api.domain.identity.settings.UserExtractionSettingsRepository
import com.briefy.api.infrastructure.id.IdGenerator
import com.briefy.api.infrastructure.security.ApiKeyEncryptionService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class UserSettingsService(
    private val userExtractionSettingsRepository: UserExtractionSettingsRepository,
    private val apiKeyEncryptionService: ApiKeyEncryptionService,
    private val idGenerator: IdGenerator
) {
    @Transactional
    fun getExtractionSettings(userId: UUID): ExtractionSettingsResponse {
        val settings = getOrCreateSettings(userId)

        return ExtractionSettingsResponse(
            providers = listOf(
                ProviderSettingDto(
                    type = FIRECRAWL,
                    enabled = settings.firecrawlEnabled,
                    configured = !settings.firecrawlApiKeyEncrypted.isNullOrBlank(),
                    platforms = FIRECRAWL_PLATFORMS,
                    description = "Preferred for article-like content and cleaner markdown extraction"
                ),
                ProviderSettingDto(
                    type = X_API,
                    enabled = settings.xApiEnabled,
                    configured = !settings.xApiBearerTokenEncrypted.isNullOrBlank(),
                    platforms = X_API_PLATFORMS,
                    description = "Preferred for X posts, threads, and article content"
                ),
                ProviderSettingDto(
                    type = JSOUP,
                    enabled = true,
                    configured = true,
                    platforms = listOf("all"),
                    description = "Always active fallback with zero configuration"
                )
            )
        )
    }

    @Transactional
    fun updateProvider(userId: UUID, command: UpdateProviderCommand): ExtractionSettingsResponse {
        if (command.type !in updatableProviders) {
            throw IllegalArgumentException("Provider '${command.type}' is not updatable")
        }

        val settings = getOrCreateSettings(userId)
        val normalizedApiKey = command.apiKey?.trim()?.takeIf { it.isNotBlank() }

        when (command.type) {
            FIRECRAWL -> {
                settings.firecrawlEnabled = command.enabled
                if (normalizedApiKey != null) {
                    settings.firecrawlApiKeyEncrypted = apiKeyEncryptionService.encrypt(normalizedApiKey)
                }
            }
            X_API -> {
                settings.xApiEnabled = command.enabled
                if (normalizedApiKey != null) {
                    settings.xApiBearerTokenEncrypted = apiKeyEncryptionService.encrypt(normalizedApiKey)
                }
            }
        }

        settings.updatedAt = Instant.now()
        userExtractionSettingsRepository.save(settings)

        return getExtractionSettings(userId)
    }

    @Transactional
    fun deleteProviderKey(userId: UUID, providerType: String): ExtractionSettingsResponse {
        if (providerType !in keyDeletableProviders) {
            throw IllegalArgumentException("Provider '$providerType' does not support deleting keys")
        }

        val settings = getOrCreateSettings(userId)
        when (providerType) {
            FIRECRAWL -> {
                settings.firecrawlEnabled = false
                settings.firecrawlApiKeyEncrypted = null
            }
            X_API -> {
                settings.xApiEnabled = false
                settings.xApiBearerTokenEncrypted = null
            }
        }
        settings.updatedAt = Instant.now()
        userExtractionSettingsRepository.save(settings)

        return getExtractionSettings(userId)
    }

    @Transactional
    fun getFirecrawlApiKey(userId: UUID): String? {
        val settings = getOrCreateSettings(userId)
        if (!settings.firecrawlEnabled || settings.firecrawlApiKeyEncrypted.isNullOrBlank()) {
            return null
        }
        return apiKeyEncryptionService.decrypt(settings.firecrawlApiKeyEncrypted!!)
    }

    @Transactional
    fun isFirecrawlEnabled(userId: UUID): Boolean {
        val settings = getOrCreateSettings(userId)
        return settings.firecrawlEnabled && !settings.firecrawlApiKeyEncrypted.isNullOrBlank()
    }

    @Transactional
    fun getXApiBearerToken(userId: UUID): String? {
        val settings = getOrCreateSettings(userId)
        if (!settings.xApiEnabled || settings.xApiBearerTokenEncrypted.isNullOrBlank()) {
            return null
        }
        return apiKeyEncryptionService.decrypt(settings.xApiBearerTokenEncrypted!!)
    }

    @Transactional
    fun isXApiEnabled(userId: UUID): Boolean {
        val settings = getOrCreateSettings(userId)
        return settings.xApiEnabled && !settings.xApiBearerTokenEncrypted.isNullOrBlank()
    }

    private fun getOrCreateSettings(userId: UUID): UserExtractionSettings {
        val existing = userExtractionSettingsRepository.findByUserId(userId)
        if (existing != null) {
            return existing
        }

        val now = Instant.now()
        return userExtractionSettingsRepository.save(
            UserExtractionSettings(
                id = idGenerator.newId(),
                userId = userId,
                firecrawlEnabled = false,
                firecrawlApiKeyEncrypted = null,
                xApiEnabled = false,
                xApiBearerTokenEncrypted = null,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    companion object {
        const val FIRECRAWL = "firecrawl"
        const val X_API = "x_api"
        const val JSOUP = "jsoup"

        private val FIRECRAWL_PLATFORMS = listOf(
            "web",
            "medium",
            "substack",
            "arxiv",
            "wikipedia",
            "github"
        )
        private val X_API_PLATFORMS = listOf("x", "twitter")
        private val updatableProviders = setOf(FIRECRAWL, X_API)
        private val keyDeletableProviders = setOf(FIRECRAWL, X_API)
    }
}
