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
