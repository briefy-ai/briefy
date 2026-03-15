package com.briefy.api.api

import com.briefy.api.application.settings.TtsSettingsResponse
import com.briefy.api.application.settings.TtsSettingsService
import com.briefy.api.application.settings.UpdatePreferredTtsProviderCommand
import com.briefy.api.application.settings.UpdateTtsProviderCommand
import com.briefy.api.infrastructure.security.CurrentUserProvider
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/settings/tts")
class TtsSettingsController(
    private val ttsSettingsService: TtsSettingsService,
    private val currentUserProvider: CurrentUserProvider
) {
    private val logger = LoggerFactory.getLogger(TtsSettingsController::class.java)

    @GetMapping
    fun getTtsSettings(): ResponseEntity<TtsSettingsResponse> {
        val userId = currentUserProvider.requireUserId()
        logger.info("[controller] Get tts settings request received userId={}", userId)
        val response = ttsSettingsService.getSettings(userId)
        logger.info("[controller] Get tts settings request completed userId={}", userId)
        return ResponseEntity.ok(response)
    }

    @PutMapping("/providers/{type}")
    fun updateProvider(
        @PathVariable type: String,
        @Valid @RequestBody request: UpdateTtsProviderRequest
    ): ResponseEntity<TtsSettingsResponse> {
        val userId = currentUserProvider.requireUserId()
        logger.info("[controller] Update tts provider request received userId={} provider={}", userId, type)
        val response = ttsSettingsService.updateProvider(
            userId,
            UpdateTtsProviderCommand(
                type = type.lowercase(),
                enabled = request.enabled,
                apiKey = request.apiKey,
                modelId = request.modelId
            )
        )
        logger.info("[controller] Update tts provider request completed userId={} provider={}", userId, type)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/providers/{type}/key")
    fun deleteProviderKey(@PathVariable type: String): ResponseEntity<TtsSettingsResponse> {
        val userId = currentUserProvider.requireUserId()
        logger.info("[controller] Delete tts provider key request received userId={} provider={}", userId, type)
        val response = ttsSettingsService.deleteProviderKey(userId, type.lowercase())
        logger.info("[controller] Delete tts provider key request completed userId={} provider={}", userId, type)
        return ResponseEntity.ok(response)
    }

    @PutMapping("/preferred-provider")
    fun updatePreferredProvider(
        @Valid @RequestBody request: UpdatePreferredTtsProviderRequest
    ): ResponseEntity<TtsSettingsResponse> {
        val userId = currentUserProvider.requireUserId()
        logger.info("[controller] Update preferred tts provider request received userId={} provider={}", userId, request.preferredProvider)
        val response = ttsSettingsService.updatePreferredProvider(
            userId,
            UpdatePreferredTtsProviderCommand(request.preferredProvider)
        )
        logger.info("[controller] Update preferred tts provider request completed userId={} provider={}", userId, request.preferredProvider)
        return ResponseEntity.ok(response)
    }
}

data class UpdateTtsProviderRequest(
    val enabled: Boolean,
    val apiKey: String? = null,
    val modelId: String? = null
)

data class UpdatePreferredTtsProviderRequest(
    val preferredProvider: String
)
