package com.briefy.api.application.settings

import com.briefy.api.domain.identity.settings.UserExtractionSettings
import com.briefy.api.domain.identity.settings.UserExtractionSettingsRepository
import com.briefy.api.infrastructure.security.ApiKeyEncryptionService
import com.briefy.api.infrastructure.tts.ElevenLabsTtsProperties
import com.briefy.api.infrastructure.tts.InworldTtsProperties
import com.briefy.api.infrastructure.tts.TtsModelCatalog
import com.briefy.api.infrastructure.tts.TtsProviderType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class ResolvedTtsProviderConfig(
    val providerType: TtsProviderType,
    val apiKey: String,
    val modelId: String
)

@Service
class TtsSettingsService(
    private val userExtractionSettingsRepository: UserExtractionSettingsRepository,
    private val apiKeyEncryptionService: ApiKeyEncryptionService,
    private val userSettingsService: UserSettingsService,
    private val ttsModelCatalog: TtsModelCatalog,
    private val elevenLabsProperties: ElevenLabsTtsProperties,
    private val inworldProperties: InworldTtsProperties
) {
    @Transactional
    fun getSettings(userId: UUID): TtsSettingsResponse {
        val settings = userSettingsService.getOrCreateSettings(userId)

        return TtsSettingsResponse(
            preferredProvider = settings.ttsPreferredProvider.apiValue,
            providers = TtsProviderType.entries.map { providerType ->
                val selectedModelId = selectedModelId(settings, providerType)
                TtsProviderSettingDto(
                    type = providerType.apiValue,
                    label = providerLabel(providerType),
                    enabled = isEnabled(settings, providerType),
                    configured = encryptedApiKey(settings, providerType) != null,
                    description = providerDescription(providerType),
                    selectedModelId = selectedModelId,
                    models = ttsModelCatalog.modelsFor(providerType).map { model ->
                        TtsModelDto(
                            id = model.id,
                            label = model.label,
                            estimatedCostPerMinuteUsd = model.estimatedCostPerMinuteUsd(CHARS_PER_MINUTE),
                            estimatedCostTenMinutesUsd = model.estimatedCostTenMinutesUsd(CHARS_PER_MINUTE)
                        )
                    }
                )
            }
        )
    }

    @Transactional
    fun updateProvider(userId: UUID, command: UpdateTtsProviderCommand): TtsSettingsResponse {
        val providerType = TtsProviderType.fromApiValue(command.type)
        val settings = userSettingsService.getOrCreateSettings(userId)
        val normalizedApiKey = command.apiKey?.trim()?.takeIf { it.isNotBlank() }
        val modelId = command.modelId?.trim()?.takeIf { it.isNotBlank() } ?: selectedModelId(settings, providerType)
        ttsModelCatalog.modelFor(providerType, modelId)

        when (providerType) {
            TtsProviderType.ELEVENLABS -> {
                settings.elevenlabsEnabled = command.enabled
                settings.elevenlabsModelId = modelId
                if (normalizedApiKey != null) {
                    settings.elevenlabsApiKeyEncrypted = apiKeyEncryptionService.encrypt(normalizedApiKey)
                }
            }
            TtsProviderType.INWORLD -> {
                settings.inworldEnabled = command.enabled
                settings.inworldModelId = modelId
                if (normalizedApiKey != null) {
                    settings.inworldApiKeyEncrypted = apiKeyEncryptionService.encrypt(normalizedApiKey)
                }
            }
        }

        settings.updatedAt = Instant.now()
        userExtractionSettingsRepository.save(settings)
        return getSettings(userId)
    }

    @Transactional
    fun deleteProviderKey(userId: UUID, providerTypeRaw: String): TtsSettingsResponse {
        val providerType = TtsProviderType.fromApiValue(providerTypeRaw)
        val settings = userSettingsService.getOrCreateSettings(userId)

        when (providerType) {
            TtsProviderType.ELEVENLABS -> {
                settings.elevenlabsEnabled = false
                settings.elevenlabsApiKeyEncrypted = null
            }
            TtsProviderType.INWORLD -> {
                settings.inworldEnabled = false
                settings.inworldApiKeyEncrypted = null
            }
        }

        settings.updatedAt = Instant.now()
        userExtractionSettingsRepository.save(settings)
        return getSettings(userId)
    }

    @Transactional
    fun updatePreferredProvider(userId: UUID, command: UpdatePreferredTtsProviderCommand): TtsSettingsResponse {
        val settings = userSettingsService.getOrCreateSettings(userId)
        val providerType = TtsProviderType.fromApiValue(command.preferredProvider)
        if (!isEnabled(settings, providerType) || encryptedApiKey(settings, providerType) == null) {
            throw IllegalArgumentException("Preferred TTS provider must be enabled and configured before selection")
        }

        settings.ttsPreferredProvider = providerType
        settings.updatedAt = Instant.now()
        userExtractionSettingsRepository.save(settings)
        return getSettings(userId)
    }

    @Transactional
    fun resolvePreferredProvider(userId: UUID): ResolvedTtsProviderConfig? {
        val settings = userSettingsService.getOrCreateSettings(userId)
        return resolveProvider(settings, settings.ttsPreferredProvider)
    }

    @Transactional
    fun preferredProviderType(userId: UUID): TtsProviderType {
        return userSettingsService.getOrCreateSettings(userId).ttsPreferredProvider
    }

    private fun resolveProvider(settings: UserExtractionSettings, providerType: TtsProviderType): ResolvedTtsProviderConfig? {
        val encryptedApiKey = encryptedApiKey(settings, providerType) ?: return null
        if (!isEnabled(settings, providerType)) {
            return null
        }

        return ResolvedTtsProviderConfig(
            providerType = providerType,
            apiKey = apiKeyEncryptionService.decrypt(encryptedApiKey),
            modelId = selectedModelId(settings, providerType)
        )
    }

    private fun providerLabel(providerType: TtsProviderType): String {
        return when (providerType) {
            TtsProviderType.ELEVENLABS -> "ElevenLabs"
            TtsProviderType.INWORLD -> "Inworld"
        }
    }

    private fun providerDescription(providerType: TtsProviderType): String {
        return when (providerType) {
            TtsProviderType.ELEVENLABS -> "Premium text-to-speech with higher-quality voices and higher per-character cost."
            TtsProviderType.INWORLD -> "Lower-cost text-to-speech for narration with server-managed default voice selection."
        }
    }

    private fun selectedModelId(settings: UserExtractionSettings, providerType: TtsProviderType): String {
        return when (providerType) {
            TtsProviderType.ELEVENLABS -> settings.elevenlabsModelId ?: elevenLabsProperties.defaultModelId
            TtsProviderType.INWORLD -> settings.inworldModelId ?: inworldProperties.defaultModelId
        }
    }

    private fun isEnabled(settings: UserExtractionSettings, providerType: TtsProviderType): Boolean {
        return when (providerType) {
            TtsProviderType.ELEVENLABS -> settings.elevenlabsEnabled
            TtsProviderType.INWORLD -> settings.inworldEnabled
        }
    }

    private fun encryptedApiKey(settings: UserExtractionSettings, providerType: TtsProviderType): String? {
        return when (providerType) {
            TtsProviderType.ELEVENLABS -> settings.elevenlabsApiKeyEncrypted
            TtsProviderType.INWORLD -> settings.inworldApiKeyEncrypted
        }?.takeIf { it.isNotBlank() }
    }
    companion object {
        const val CHARS_PER_MINUTE = 1_000
    }
}
