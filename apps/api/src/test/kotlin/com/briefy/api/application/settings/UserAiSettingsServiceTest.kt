package com.briefy.api.application.settings

import com.briefy.api.domain.identity.settings.UserAiSettings
import com.briefy.api.domain.identity.settings.UserAiSettingsRepository
import com.briefy.api.infrastructure.id.IdGenerator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class UserAiSettingsServiceTest {
    private val userAiSettingsRepository: UserAiSettingsRepository = mock()
    private val idGenerator: IdGenerator = mock()

    private fun service(
        zhipuApiKey: String = "",
        minimaxApiKey: String = "",
        googleGenAiApiKey: String = ""
    ) = UserAiSettingsService(
        userAiSettingsRepository = userAiSettingsRepository,
        idGenerator = idGenerator,
        zhipuApiKey = zhipuApiKey,
        minimaxApiKey = minimaxApiKey,
        googleGenAiApiKey = googleGenAiApiKey
    )

    @Test
    fun `get ai settings marks zhipu provider as deprecated`() {
        val userId = UUID.randomUUID()
        whenever(userAiSettingsRepository.findByUserId(userId)).thenReturn(
            existingSettings(
                userId = userId,
                topicProvider = UserAiSettingsService.PROVIDER_GOOGLE_GENAI,
                topicModel = "gemini-2.5-flash",
                formattingProvider = UserAiSettingsService.PROVIDER_MINIMAX,
                formattingModel = "MiniMax-M2.5"
            )
        )

        val response = service(
            zhipuApiKey = "zhipu-key",
            minimaxApiKey = "minimax-key",
            googleGenAiApiKey = "google-key"
        ).getAiSettings(userId)

        val zhipuProvider = response.providers.first { it.id == UserAiSettingsService.PROVIDER_ZHIPUAI }
        val googleProvider = response.providers.first { it.id == UserAiSettingsService.PROVIDER_GOOGLE_GENAI }

        assertTrue(zhipuProvider.deprecated)
        assertFalse(googleProvider.deprecated)
    }

    @Test
    fun `get ai settings creates google defaults for new users when configured`() {
        val userId = UUID.randomUUID()
        val settingsId = UUID.randomUUID()
        whenever(userAiSettingsRepository.findByUserId(userId)).thenReturn(null)
        whenever(idGenerator.newId()).thenReturn(settingsId)
        whenever(userAiSettingsRepository.save(any())).thenAnswer { it.arguments[0] as UserAiSettings }

        val response = service(
            zhipuApiKey = "zhipu-key",
            minimaxApiKey = "minimax-key",
            googleGenAiApiKey = "google-key"
        ).getAiSettings(userId)

        val savedSettings = argumentCaptor<UserAiSettings>().also {
            verify(userAiSettingsRepository).save(it.capture())
        }.firstValue

        assertEquals(UserAiSettingsService.PROVIDER_GOOGLE_GENAI, savedSettings.topicExtractionProvider)
        assertEquals("gemini-2.5-flash", savedSettings.topicExtractionModel)
        assertEquals(UserAiSettingsService.PROVIDER_GOOGLE_GENAI, savedSettings.sourceFormattingProvider)
        assertEquals("gemini-2.5-flash", savedSettings.sourceFormattingModel)
        assertEquals(UserAiSettingsService.PROVIDER_GOOGLE_GENAI, response.useCases[0].provider)
        assertEquals(UserAiSettingsService.PROVIDER_GOOGLE_GENAI, response.useCases[1].provider)
    }

    @Test
    fun `get ai settings falls back to minimax before zhipu for new users`() {
        val userId = UUID.randomUUID()
        whenever(userAiSettingsRepository.findByUserId(userId)).thenReturn(null)
        whenever(idGenerator.newId()).thenReturn(UUID.randomUUID())
        whenever(userAiSettingsRepository.save(any())).thenAnswer { it.arguments[0] as UserAiSettings }

        service(
            zhipuApiKey = "zhipu-key",
            minimaxApiKey = "minimax-key",
            googleGenAiApiKey = ""
        ).getAiSettings(userId)

        val savedSettings = argumentCaptor<UserAiSettings>().also {
            verify(userAiSettingsRepository).save(it.capture())
        }.firstValue

        assertEquals(UserAiSettingsService.PROVIDER_MINIMAX, savedSettings.topicExtractionProvider)
        assertEquals("MiniMax-M2.5", savedSettings.topicExtractionModel)
        assertEquals(UserAiSettingsService.PROVIDER_MINIMAX, savedSettings.sourceFormattingProvider)
        assertEquals("MiniMax-M2.5", savedSettings.sourceFormattingModel)
    }

    @Test
    fun `get ai settings preserves existing zhipu selections`() {
        val userId = UUID.randomUUID()
        whenever(userAiSettingsRepository.findByUserId(userId)).thenReturn(
            existingSettings(
                userId = userId,
                topicProvider = UserAiSettingsService.PROVIDER_ZHIPUAI,
                topicModel = "glm-4.7-flash",
                formattingProvider = UserAiSettingsService.PROVIDER_ZHIPUAI,
                formattingModel = "glm-4.7"
            )
        )

        val response = service(
            zhipuApiKey = "zhipu-key",
            minimaxApiKey = "minimax-key",
            googleGenAiApiKey = "google-key"
        ).getAiSettings(userId)

        verify(userAiSettingsRepository, never()).save(any())
        assertEquals(UserAiSettingsService.PROVIDER_ZHIPUAI, response.useCases[0].provider)
        assertEquals("glm-4.7-flash", response.useCases[0].model)
        assertEquals(UserAiSettingsService.PROVIDER_ZHIPUAI, response.useCases[1].provider)
        assertEquals("glm-4.7", response.useCases[1].model)
    }

    private fun existingSettings(
        userId: UUID,
        topicProvider: String,
        topicModel: String,
        formattingProvider: String,
        formattingModel: String
    ) = UserAiSettings(
        id = UUID.randomUUID(),
        userId = userId,
        topicExtractionProvider = topicProvider,
        topicExtractionModel = topicModel,
        sourceFormattingProvider = formattingProvider,
        sourceFormattingModel = formattingModel,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )
}
