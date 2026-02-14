package com.briefy.api.api

import com.briefy.api.application.auth.UnauthorizedException
import com.briefy.api.application.telegram.TelegramLinkCodeResponse
import com.briefy.api.application.telegram.TelegramLinkService
import com.briefy.api.application.telegram.TelegramLinkStatusResponse
import com.briefy.api.application.telegram.TelegramWebhookService
import com.briefy.api.config.TelegramProperties
import com.briefy.api.infrastructure.security.CurrentUserProvider
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.telegram.telegrambots.meta.api.objects.Update

@RestController
@RequestMapping("/api/settings/integrations/telegram")
class TelegramSettingsController(
    private val currentUserProvider: CurrentUserProvider,
    private val telegramLinkService: TelegramLinkService
) {
    private val logger = LoggerFactory.getLogger(TelegramSettingsController::class.java)

    @GetMapping
    fun getTelegramStatus(): ResponseEntity<TelegramLinkStatusResponse> {
        val userId = currentUserProvider.requireUserId()
        logger.info("[controller] Get telegram status request received userId={}", userId)
        return ResponseEntity.ok(telegramLinkService.getLinkStatus(userId))
    }

    @PostMapping("/link-code")
    fun generateLinkCode(): ResponseEntity<TelegramLinkCodeResponse> {
        val userId = currentUserProvider.requireUserId()
        logger.info("[controller] Generate telegram link code request received userId={}", userId)
        val response = telegramLinkService.generateLinkCode(userId)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/link")
    fun unlinkTelegram(): ResponseEntity<Unit> {
        val userId = currentUserProvider.requireUserId()
        logger.info("[controller] Unlink telegram request received userId={}", userId)
        telegramLinkService.unlinkByUserId(userId)
        return ResponseEntity.noContent().build()
    }
}

@RestController
@RequestMapping("/api/integrations/telegram")
class TelegramWebhookController(
    private val telegramWebhookService: TelegramWebhookService,
    private val telegramProperties: TelegramProperties
) {
    @PostMapping("/webhook")
    fun receiveWebhook(
        @RequestHeader(name = "X-Telegram-Bot-Api-Secret-Token", required = false) secretToken: String?,
        @RequestBody update: Update
    ): ResponseEntity<Unit> {
        if (!telegramProperties.integration.enabled) {
            return ResponseEntity.accepted().build()
        }

        val expectedSecret = telegramProperties.webhook.secretToken.trim()
        if (expectedSecret.isBlank() || secretToken != expectedSecret) {
            throw UnauthorizedException("Invalid telegram webhook secret")
        }

        telegramWebhookService.handleUpdate(update)
        return ResponseEntity.ok().build()
    }
}
