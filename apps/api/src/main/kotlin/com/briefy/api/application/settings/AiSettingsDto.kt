package com.briefy.api.application.settings

data class AiSettingsResponse(
    val providers: List<AiProviderDto>,
    val useCases: List<AiUseCaseSettingDto>
)

data class AiProviderDto(
    val id: String,
    val label: String,
    val configured: Boolean,
    val models: List<AiModelDto>
)

data class AiModelDto(
    val id: String,
    val label: String
)

data class AiUseCaseSettingDto(
    val id: String,
    val provider: String,
    val model: String
)

data class UpdateAiUseCaseCommand(
    val useCase: String,
    val provider: String,
    val model: String
)

data class AiModelSelection(
    val provider: String,
    val model: String
)
