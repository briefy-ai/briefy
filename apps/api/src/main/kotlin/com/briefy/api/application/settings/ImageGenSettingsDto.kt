package com.briefy.api.application.settings

data class ImageGenSettingsResponse(
    val enabled: Boolean,
    val configured: Boolean,
    val selectedModel: String,
    val models: List<ImageGenModelDto>
)

data class ImageGenModelDto(
    val id: String,
    val label: String
)

data class UpdateImageGenProviderCommand(
    val enabled: Boolean,
    val apiKey: String? = null,
    val modelId: String? = null
)
