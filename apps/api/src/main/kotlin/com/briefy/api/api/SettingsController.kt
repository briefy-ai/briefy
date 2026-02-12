package com.briefy.api.api

import com.briefy.api.application.settings.ExtractionSettingsResponse
import com.briefy.api.application.settings.UpdateProviderCommand
import com.briefy.api.application.settings.UserSettingsService
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
@RequestMapping("/api/settings/extraction")
class SettingsController(
    private val userSettingsService: UserSettingsService,
    private val currentUserProvider: CurrentUserProvider
) {
    private val logger = LoggerFactory.getLogger(SettingsController::class.java)

    @GetMapping
    fun getExtractionSettings(): ResponseEntity<ExtractionSettingsResponse> {
        val userId = currentUserProvider.requireUserId()
        logger.info("[controller] Get extraction settings request received userId={}", userId)
        val response = userSettingsService.getExtractionSettings(userId)
        logger.info("[controller] Get extraction settings request completed userId={}", userId)
        return ResponseEntity.ok(response)
    }

    @PutMapping("/providers/{type}")
    fun updateProvider(
        @PathVariable type: String,
        @Valid @RequestBody request: UpdateProviderRequest
    ): ResponseEntity<ExtractionSettingsResponse> {
        val userId = currentUserProvider.requireUserId()
        logger.info("[controller] Update extraction provider request received userId={} provider={}", userId, type)
        val response = userSettingsService.updateProvider(
            userId,
            UpdateProviderCommand(
                type = type.lowercase(),
                enabled = request.enabled,
                apiKey = request.apiKey
            )
        )
        logger.info("[controller] Update extraction provider request completed userId={} provider={}", userId, type)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/providers/{type}/key")
    fun deleteProviderKey(@PathVariable type: String): ResponseEntity<ExtractionSettingsResponse> {
        val userId = currentUserProvider.requireUserId()
        logger.info("[controller] Delete extraction provider key request received userId={} provider={}", userId, type)
        val response = userSettingsService.deleteProviderKey(userId, type.lowercase())
        logger.info("[controller] Delete extraction provider key request completed userId={} provider={}", userId, type)
        return ResponseEntity.ok(response)
    }
}

data class UpdateProviderRequest(
    val enabled: Boolean,
    val apiKey: String? = null
)
