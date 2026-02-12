package com.briefy.api.application.settings

import com.briefy.api.domain.identity.settings.UserAiSettings
import com.briefy.api.domain.identity.settings.UserAiSettingsRepository
import com.briefy.api.infrastructure.id.IdGenerator
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class UserAiSettingsService(
    private val userAiSettingsRepository: UserAiSettingsRepository,
    private val idGenerator: IdGenerator,
    @param:Value("\${spring.ai.model.chat:none}")
    private val chatProvider: String,
    @param:Value("\${spring.ai.zhipuai.api-key:}")
    private val zhipuApiKey: String,
    @param:Value("\${spring.ai.google.genai.api-key:}")
    private val googleGenAiApiKey: String
) {
    @Transactional
    fun getAiSettings(userId: UUID): AiSettingsResponse {
        val settings = getOrCreateSettings(userId)
        val providers = buildProviders()
        return AiSettingsResponse(
            providers = providers,
            useCases = listOf(
                AiUseCaseSettingDto(
                    id = TOPIC_EXTRACTION,
                    provider = settings.topicExtractionProvider,
                    model = settings.topicExtractionModel
                ),
                AiUseCaseSettingDto(
                    id = SOURCE_FORMATTING,
                    provider = settings.sourceFormattingProvider,
                    model = settings.sourceFormattingModel
                )
            )
        )
    }

    @Transactional
    fun updateUseCase(userId: UUID, command: UpdateAiUseCaseCommand): AiSettingsResponse {
        val settings = getOrCreateSettings(userId)
        val normalizedUseCase = command.useCase.trim().lowercase()
        val normalizedProvider = command.provider.trim().lowercase()
        val normalizedModel = command.model.trim()

        validateUseCase(normalizedUseCase)
        validateProviderModel(normalizedProvider, normalizedModel)
        require(isProviderConfigured(normalizedProvider)) { "Provider '$normalizedProvider' is not configured on this server" }

        when (normalizedUseCase) {
            TOPIC_EXTRACTION -> {
                settings.topicExtractionProvider = normalizedProvider
                settings.topicExtractionModel = normalizedModel
            }
            SOURCE_FORMATTING -> {
                settings.sourceFormattingProvider = normalizedProvider
                settings.sourceFormattingModel = normalizedModel
            }
        }
        settings.updatedAt = Instant.now()
        userAiSettingsRepository.save(settings)

        return getAiSettings(userId)
    }

    @Transactional
    fun resolveUseCaseSelection(userId: UUID, useCase: String): AiModelSelection {
        val settings = getOrCreateSettings(userId)
        val normalizedUseCase = useCase.trim().lowercase()
        validateUseCase(normalizedUseCase)

        val selection = when (normalizedUseCase) {
            TOPIC_EXTRACTION -> AiModelSelection(
                provider = settings.topicExtractionProvider,
                model = settings.topicExtractionModel
            )
            SOURCE_FORMATTING -> AiModelSelection(
                provider = settings.sourceFormattingProvider,
                model = settings.sourceFormattingModel
            )
            else -> throw IllegalArgumentException("Unsupported use case: '$normalizedUseCase'")
        }

        validateProviderModel(selection.provider, selection.model)
        require(isProviderConfigured(selection.provider)) {
            "Configured provider '${selection.provider}' is not available on this server"
        }

        return selection
    }

    private fun buildProviders(): List<AiProviderDto> {
        return listOf(
            AiProviderDto(
                id = PROVIDER_ZHIPUAI,
                label = "ZhipuAI",
                configured = isProviderConfigured(PROVIDER_ZHIPUAI),
                models = ZHIPU_MODELS.map { AiModelDto(id = it, label = it) }
            ),
            AiProviderDto(
                id = PROVIDER_GOOGLE_GENAI,
                label = "Google GenAI",
                configured = isProviderConfigured(PROVIDER_GOOGLE_GENAI),
                models = GOOGLE_GENAI_MODELS.map { AiModelDto(id = it, label = it) }
            )
        )
    }

    private fun isProviderConfigured(provider: String): Boolean {
        return when (provider) {
            PROVIDER_ZHIPUAI -> zhipuApiKey.isNotBlank() && chatProvider.equals("zhipuai", ignoreCase = true)
            PROVIDER_GOOGLE_GENAI -> googleGenAiApiKey.isNotBlank()
            else -> false
        }
    }

    private fun validateUseCase(useCase: String) {
        require(useCase in USE_CASES) { "Unsupported use case '$useCase'" }
    }

    private fun validateProviderModel(provider: String, model: String) {
        val allowedModels = when (provider) {
            PROVIDER_ZHIPUAI -> ZHIPU_MODELS
            PROVIDER_GOOGLE_GENAI -> GOOGLE_GENAI_MODELS
            else -> throw IllegalArgumentException("Unsupported provider '$provider'")
        }
        require(model in allowedModels) { "Model '$model' is not supported for provider '$provider'" }
    }

    private fun getOrCreateSettings(userId: UUID): UserAiSettings {
        val existing = userAiSettingsRepository.findByUserId(userId)
        if (existing != null) {
            return existing
        }

        val now = Instant.now()
        return userAiSettingsRepository.save(
            UserAiSettings(
                id = idGenerator.newId(),
                userId = userId,
                topicExtractionProvider = DEFAULT_TOPIC_PROVIDER,
                topicExtractionModel = DEFAULT_TOPIC_MODEL,
                sourceFormattingProvider = DEFAULT_SOURCE_FORMATTING_PROVIDER,
                sourceFormattingModel = DEFAULT_SOURCE_FORMATTING_MODEL,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    companion object {
        const val TOPIC_EXTRACTION = "topic_extraction"
        const val SOURCE_FORMATTING = "source_formatting"

        const val PROVIDER_ZHIPUAI = "zhipuai"
        const val PROVIDER_GOOGLE_GENAI = "google_genai"

        const val DEFAULT_TOPIC_PROVIDER = PROVIDER_ZHIPUAI
        const val DEFAULT_TOPIC_MODEL = "glm-4.7-flash"
        const val DEFAULT_SOURCE_FORMATTING_PROVIDER = PROVIDER_ZHIPUAI
        const val DEFAULT_SOURCE_FORMATTING_MODEL = "glm-4.7"

        val GOOGLE_GENAI_MODELS = listOf(
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
            "gemini-3-flash-preview"
        )
        val ZHIPU_MODELS = listOf(
            "glm-4.7-flash",
            "glm-4.7",
            "glm-5"
        )

        private val USE_CASES = setOf(TOPIC_EXTRACTION, SOURCE_FORMATTING)
    }
}
