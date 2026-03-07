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
    @param:Value("\${spring.ai.zhipuai.api-key:}")
    private val zhipuApiKey: String,
    @param:Value("\${spring.ai.minimax.api-key:}")
    private val minimaxApiKey: String,
    @param:Value("\${spring.ai.google.genai.api-key:}")
    private val googleGenAiApiKey: String,
    @param:Value("\${briefing.execution.ai.subagent.provider:\${briefing.execution.ai.provider:google_genai}}")
    private val defaultBriefingSubagentProvider: String,
    @param:Value("\${briefing.execution.ai.subagent.model:\${briefing.execution.ai.model:gemini-2.5-flash}}")
    private val defaultBriefingSubagentModel: String,
    @param:Value("\${briefing.execution.ai.synthesis.provider:\${briefing.execution.ai.provider:google_genai}}")
    private val defaultBriefingSynthesisProvider: String,
    @param:Value("\${briefing.execution.ai.synthesis.model:\${briefing.execution.ai.model:gemini-2.5-flash}}")
    private val defaultBriefingSynthesisModel: String
) {
    @Transactional
    fun getAiSettings(userId: UUID): AiSettingsResponse {
        val settings = getOrCreateSettings(userId)
        val providers = buildProviders()
        return AiSettingsResponse(
            providers = providers,
            useCases = USE_CASES.map { useCase ->
                val selection = storedSelectionForUseCase(settings, useCase)
                    ?: defaultSelectionForUseCase(useCase)
                AiUseCaseSettingDto(
                    id = useCase,
                    provider = selection.provider,
                    model = selection.model
                )
            }
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
            BRIEFING_SUBAGENT_EXECUTION -> {
                settings.briefingSubagentExecutionProvider = normalizedProvider
                settings.briefingSubagentExecutionModel = normalizedModel
            }
            BRIEFING_SYNTHESIS -> {
                settings.briefingSynthesisProvider = normalizedProvider
                settings.briefingSynthesisModel = normalizedModel
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

        val selection = storedSelectionForUseCase(settings, normalizedUseCase)
            ?: throw IllegalArgumentException("Use case '$normalizedUseCase' is not configured")

        validateProviderModel(selection.provider, selection.model)
        require(isProviderConfigured(selection.provider)) {
            "Configured provider '${selection.provider}' is not available on this server"
        }

        return selection
    }

    @Transactional
    fun resolveUseCaseSelectionWithFallback(userId: UUID, useCase: String): AiModelSelection {
        val settings = getOrCreateSettings(userId)
        return resolveUseCaseSelectionWithFallback(settings, useCase.trim().lowercase())
    }

    private fun resolveUseCaseSelectionWithFallback(settings: UserAiSettings, useCase: String): AiModelSelection {
        validateUseCase(useCase)

        val persistedSelection = storedSelectionForUseCase(settings, useCase)
        if (persistedSelection != null && isSelectionUsable(persistedSelection)) {
            return persistedSelection
        }

        val fallbackSelection = defaultSelectionForUseCase(useCase)
        validateProviderModel(fallbackSelection.provider, fallbackSelection.model)
        require(isProviderConfigured(fallbackSelection.provider)) {
            "Default provider '${fallbackSelection.provider}' for use case '$useCase' is not configured on this server"
        }
        return fallbackSelection
    }

    private fun isSelectionUsable(selection: AiModelSelection): Boolean {
        return runCatching {
            validateProviderModel(selection.provider, selection.model)
            isProviderConfigured(selection.provider)
        }.getOrDefault(false)
    }

    private fun storedSelectionForUseCase(settings: UserAiSettings, useCase: String): AiModelSelection? {
        return when (useCase) {
            TOPIC_EXTRACTION -> normalizeSelection(
                provider = settings.topicExtractionProvider,
                model = settings.topicExtractionModel
            )
            SOURCE_FORMATTING -> normalizeSelection(
                provider = settings.sourceFormattingProvider,
                model = settings.sourceFormattingModel
            )
            BRIEFING_SUBAGENT_EXECUTION -> normalizeSelection(
                provider = settings.briefingSubagentExecutionProvider,
                model = settings.briefingSubagentExecutionModel
            )
            BRIEFING_SYNTHESIS -> normalizeSelection(
                provider = settings.briefingSynthesisProvider,
                model = settings.briefingSynthesisModel
            )
            else -> null
        }
    }

    private fun defaultSelectionForUseCase(useCase: String): AiModelSelection {
        return when (useCase) {
            TOPIC_EXTRACTION -> AiModelSelection(
                provider = DEFAULT_TOPIC_PROVIDER,
                model = DEFAULT_TOPIC_MODEL
            )
            SOURCE_FORMATTING -> AiModelSelection(
                provider = DEFAULT_SOURCE_FORMATTING_PROVIDER,
                model = DEFAULT_SOURCE_FORMATTING_MODEL
            )
            BRIEFING_SUBAGENT_EXECUTION -> AiModelSelection(
                provider = defaultBriefingSubagentProvider.trim().lowercase(),
                model = defaultBriefingSubagentModel.trim()
            )
            BRIEFING_SYNTHESIS -> AiModelSelection(
                provider = defaultBriefingSynthesisProvider.trim().lowercase(),
                model = defaultBriefingSynthesisModel.trim()
            )
            else -> throw IllegalArgumentException("Unsupported use case '$useCase'")
        }
    }

    private fun normalizeSelection(provider: String?, model: String?): AiModelSelection? {
        val normalizedProvider = provider?.trim()?.lowercase().orEmpty()
        val normalizedModel = model?.trim().orEmpty()
        if (normalizedProvider.isBlank() || normalizedModel.isBlank()) {
            return null
        }
        return AiModelSelection(provider = normalizedProvider, model = normalizedModel)
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
            ),
            AiProviderDto(
                id = PROVIDER_MINIMAX,
                label = "MiniMax",
                configured = isProviderConfigured(PROVIDER_MINIMAX),
                models = MINIMAX_MODELS.map { AiModelDto(id = it, label = it) }
            )
        )
    }

    private fun isProviderConfigured(provider: String): Boolean {
        return when (provider) {
            PROVIDER_ZHIPUAI -> zhipuApiKey.isNotBlank()
            PROVIDER_GOOGLE_GENAI -> googleGenAiApiKey.isNotBlank()
            PROVIDER_MINIMAX -> minimaxApiKey.isNotBlank()
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
            PROVIDER_MINIMAX -> MINIMAX_MODELS
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
        const val BRIEFING_SUBAGENT_EXECUTION = "briefing_subagent_execution"
        const val BRIEFING_SYNTHESIS = "briefing_synthesis"

        const val PROVIDER_ZHIPUAI = "zhipuai"
        const val PROVIDER_GOOGLE_GENAI = "google_genai"
        const val PROVIDER_MINIMAX = "minimax"

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
        val MINIMAX_MODELS = listOf(
            "MiniMax-M2.5"
        )

        val USE_CASES = listOf(
            TOPIC_EXTRACTION,
            SOURCE_FORMATTING,
            BRIEFING_SUBAGENT_EXECUTION,
            BRIEFING_SYNTHESIS
        )
    }
}
