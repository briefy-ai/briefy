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
import org.springframework.boot.json.JsonParserFactory
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

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
    private val logger = LoggerFactory.getLogger(TelegramWebhookController::class.java)

    @PostMapping("/webhook")
    fun receiveWebhook(
        @RequestHeader(name = "X-Telegram-Bot-Api-Secret-Token", required = false) secretToken: String?,
        @RequestBody rawBody: String
    ): ResponseEntity<Unit> {
        val payload = runCatching {
            JsonParserFactory.getJsonParser().parseMap(rawBody)
        }.getOrElse { error ->
            logger.warn("[telegram] webhook_rejected reason=invalid_json", error)
            return ResponseEntity.badRequest().build()
        }

        val message = payload["message"] as? Map<*, *> ?: emptyMap<String, Any?>()
        val from = message["from"] as? Map<*, *> ?: emptyMap<String, Any?>()
        val chat = message["chat"] as? Map<*, *> ?: emptyMap<String, Any?>()
        val updateId = payload["update_id"].toLongOrNull()
        val messageId = message["message_id"].toLongOrNull()
        val fromId = from["id"].toLongOrNull()
        val chatId = chat["id"].toLongOrNull()
        val chatType = chat["type"]?.toString().orEmpty()
        val username = from["username"]?.toString()
        val text = message["text"]?.toString() ?: message["caption"]?.toString().orEmpty()

        logger.info(
            "[telegram] webhook_received integrationEnabled={} updateId={} messageId={} fromId={} chatId={}",
            telegramProperties.integration.enabled,
            updateId,
            messageId,
            fromId,
            chatId
        )
        if (!telegramProperties.integration.enabled) {
            return ResponseEntity.accepted().build()
        }

        val expectedSecret = telegramProperties.webhook.secretToken.trim()
        if (expectedSecret.isBlank() || secretToken != expectedSecret) {
            logger.warn(
                "[telegram] webhook_rejected reason=invalid_secret updateId={} headerPresent={} expectedConfigured={}",
                updateId,
                !secretToken.isNullOrBlank(),
                expectedSecret.isNotBlank()
            )
            throw UnauthorizedException("Invalid telegram webhook secret")
        }

        logger.info("[telegram] webhook_accepted updateId={}", updateId)
        telegramWebhookService.handleIncomingMessage(
            telegramUserId = fromId,
            telegramChatId = chatId,
            telegramMessageId = messageId,
            chatType = chatType,
            username = username?.ifBlank { null },
            text = text
        )
        return ResponseEntity.ok().build()
    }
}

private fun Any?.toLongOrNull(): Long? {
    return when (this) {
        is Number -> this.toLong()
        is String -> this.toLongOrNull()
        else -> null
    }
}
