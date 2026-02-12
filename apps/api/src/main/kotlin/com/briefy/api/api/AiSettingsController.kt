package com.briefy.api.api

import com.briefy.api.application.settings.AiSettingsResponse
import com.briefy.api.application.settings.UpdateAiUseCaseCommand
import com.briefy.api.application.settings.UserAiSettingsService
import com.briefy.api.infrastructure.security.CurrentUserProvider
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/settings/ai")
class AiSettingsController(
    private val userAiSettingsService: UserAiSettingsService,
    private val currentUserProvider: CurrentUserProvider
) {
    private val logger = LoggerFactory.getLogger(AiSettingsController::class.java)

    @GetMapping
    fun getAiSettings(): ResponseEntity<AiSettingsResponse> {
        val userId = currentUserProvider.requireUserId()
        logger.info("[controller] Get AI settings request received userId={}", userId)
        val response = userAiSettingsService.getAiSettings(userId)
        logger.info("[controller] Get AI settings request completed userId={}", userId)
        return ResponseEntity.ok(response)
    }

    @PutMapping("/use-cases/{useCase}")
    fun updateUseCase(
        @PathVariable useCase: String,
        @Valid @RequestBody request: UpdateAiUseCaseRequest
    ): ResponseEntity<AiSettingsResponse> {
        val userId = currentUserProvider.requireUserId()
        logger.info("[controller] Update AI use case request received userId={} useCase={}", userId, useCase)
        val response = userAiSettingsService.updateUseCase(
            userId = userId,
            command = UpdateAiUseCaseCommand(
                useCase = useCase,
                provider = request.provider,
                model = request.model
            )
        )
        logger.info("[controller] Update AI use case request completed userId={} useCase={}", userId, useCase)
        return ResponseEntity.ok(response)
    }
}

data class UpdateAiUseCaseRequest(
    val provider: String,
    val model: String
)
