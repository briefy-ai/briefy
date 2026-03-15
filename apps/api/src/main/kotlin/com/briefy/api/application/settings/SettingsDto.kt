package com.briefy.api.application.settings

data class ExtractionSettingsResponse(
    val providers: List<ProviderSettingDto>
)

data class ProviderSettingDto(
    val type: String,
    val enabled: Boolean,
    val configured: Boolean,
    val platforms: List<String>,
    val description: String
)

data class UpdateProviderCommand(
    val type: String,
    val enabled: Boolean,
    val apiKey: String?
)

data class TtsSettingsResponse(
    val preferredProvider: String,
    val providers: List<TtsProviderSettingDto>
)

data class TtsProviderSettingDto(
    val type: String,
    val label: String,
    val enabled: Boolean,
    val configured: Boolean,
    val description: String,
    val selectedModelId: String,
    val models: List<TtsModelDto>
)

data class TtsModelDto(
    val id: String,
    val label: String,
    val estimatedCostPerMinuteUsd: java.math.BigDecimal,
    val estimatedCostTenMinutesUsd: java.math.BigDecimal
)

data class UpdateTtsProviderCommand(
    val type: String,
    val enabled: Boolean,
    val apiKey: String?,
    val modelId: String?
)

data class UpdatePreferredTtsProviderCommand(
    val preferredProvider: String
)
