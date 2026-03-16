package com.briefy.api.application.settings

import com.briefy.api.domain.identity.settings.UserExtractionSettings
import com.briefy.api.domain.identity.settings.UserExtractionSettingsRepository
import com.briefy.api.infrastructure.security.ApiKeyEncryptionService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class ResolvedImageGenConfig(
    val apiKey: String,
    val model: String
)

@Service
class ImageGenSettingsService(
    private val userExtractionSettingsRepository: UserExtractionSettingsRepository,
    private val apiKeyEncryptionService: ApiKeyEncryptionService,
    private val userSettingsService: UserSettingsService
) {
    @Transactional
    fun getSettings(userId: UUID): ImageGenSettingsResponse {
        val settings = userSettingsService.getOrCreateSettings(userId)
        return ImageGenSettingsResponse(
            enabled = settings.openrouterEnabled,
            configured = encryptedApiKey(settings) != null,
            selectedModel = selectedModel(settings),
            models = MODELS
        )
    }

    @Transactional
    fun updateProvider(userId: UUID, command: UpdateImageGenProviderCommand): ImageGenSettingsResponse {
        val settings = userSettingsService.getOrCreateSettings(userId)
        val normalizedApiKey = command.apiKey?.trim()?.takeIf { it.isNotBlank() }
        val modelId = command.modelId?.trim()?.takeIf { it.isNotBlank() } ?: selectedModel(settings)
        require(MODELS.any { it.id == modelId }) { "Unsupported image generation model '$modelId'" }

        settings.openrouterEnabled = command.enabled
        settings.openrouterImageModel = modelId
        if (normalizedApiKey != null) {
            settings.openrouterApiKeyEncrypted = apiKeyEncryptionService.encrypt(normalizedApiKey)
        }

        settings.updatedAt = Instant.now()
        userExtractionSettingsRepository.save(settings)
        return getSettings(userId)
    }

    @Transactional
    fun deleteProviderKey(userId: UUID): ImageGenSettingsResponse {
        val settings = userSettingsService.getOrCreateSettings(userId)
        settings.openrouterEnabled = false
        settings.openrouterApiKeyEncrypted = null
        settings.updatedAt = Instant.now()
        userExtractionSettingsRepository.save(settings)
        return getSettings(userId)
    }

    @Transactional
    fun resolveConfig(userId: UUID): ResolvedImageGenConfig? {
        val settings = userSettingsService.getOrCreateSettings(userId)
        if (!settings.openrouterEnabled) {
            return null
        }

        val encryptedApiKey = encryptedApiKey(settings) ?: return null
        return ResolvedImageGenConfig(
            apiKey = apiKeyEncryptionService.decrypt(encryptedApiKey),
            model = selectedModel(settings)
        )
    }

    private fun selectedModel(settings: UserExtractionSettings): String {
        return settings.openrouterImageModel?.takeIf { modelId -> MODELS.any { it.id == modelId } }
            ?: DEFAULT_MODEL_ID
    }

    private fun encryptedApiKey(settings: UserExtractionSettings): String? {
        return settings.openrouterApiKeyEncrypted?.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val DEFAULT_MODEL_ID = "google/gemini-3.1-flash-image-preview"

        private val MODELS = listOf(
            ImageGenModelDto(
                id = "google/gemini-3.1-flash-image-preview",
                label = "Gemini 3.1 Flash Image Preview"
            ),
            ImageGenModelDto(
                id = "bytedance-seed/seedream-4.5",
                label = "Seedream 4.5"
            ),
            ImageGenModelDto(
                id = "black-forest-labs/flux.2-max",
                label = "FLUX.2 Max"
            )
        )
    }
}
