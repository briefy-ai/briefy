package com.briefy.api.api

import com.briefy.api.application.settings.ImageGenSettingsResponse
import com.briefy.api.application.settings.ImageGenSettingsService
import com.briefy.api.application.settings.UpdateImageGenProviderCommand
import com.briefy.api.infrastructure.security.CurrentUserProvider
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/settings/image-gen")
class ImageGenSettingsController(
    private val imageGenSettingsService: ImageGenSettingsService,
    private val currentUserProvider: CurrentUserProvider
) {
    private val logger = LoggerFactory.getLogger(ImageGenSettingsController::class.java)

    @GetMapping
    fun getSettings(): ResponseEntity<ImageGenSettingsResponse> {
        val userId = currentUserProvider.requireUserId()
        logger.info("[controller] Get image generation settings request received userId={}", userId)
        val response = imageGenSettingsService.getSettings(userId)
        logger.info("[controller] Get image generation settings request completed userId={}", userId)
        return ResponseEntity.ok(response)
    }

    @PutMapping("/provider")
    fun updateProvider(
        @Valid @RequestBody request: UpdateImageGenProviderCommand
    ): ResponseEntity<ImageGenSettingsResponse> {
        val userId = currentUserProvider.requireUserId()
        logger.info("[controller] Update image generation settings request received userId={}", userId)
        val response = imageGenSettingsService.updateProvider(userId, request)
        logger.info("[controller] Update image generation settings request completed userId={}", userId)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/provider/key")
    fun deleteProviderKey(): ResponseEntity<ImageGenSettingsResponse> {
        val userId = currentUserProvider.requireUserId()
        logger.info("[controller] Delete image generation key request received userId={}", userId)
        val response = imageGenSettingsService.deleteProviderKey(userId)
        logger.info("[controller] Delete image generation key request completed userId={}", userId)
        return ResponseEntity.ok(response)
    }
}
