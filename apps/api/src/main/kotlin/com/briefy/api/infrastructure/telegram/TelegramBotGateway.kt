package com.briefy.api.infrastructure.telegram

import com.briefy.api.config.TelegramProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class TelegramBotGateway(
    restClientBuilder: RestClient.Builder,
    private val telegramProperties: TelegramProperties
) {
    private val logger = LoggerFactory.getLogger(TelegramBotGateway::class.java)
    private val restClient = restClientBuilder.baseUrl("https://api.telegram.org").build()

    fun sendMessage(chatId: Long, text: String) {
        if (!isEnabled()) return

        var attempts = 0
        var delayMs = INITIAL_RETRY_DELAY_MS
        while (attempts < MAX_SEND_ATTEMPTS) {
            attempts++
            try {
                restClient.post()
                    .uri("/bot${telegramProperties.bot.token}/sendMessage")
                    .body(mapOf("chat_id" to chatId, "text" to text))
                    .retrieve()
                    .toBodilessEntity()
                return
            } catch (e: Exception) {
                logger.warn(
                    "[telegram] sendMessage failed chatId={} attempt={}",
                    chatId,
                    attempts,
                    e
                )
                if (attempts >= MAX_SEND_ATTEMPTS) {
                    throw e
                }
                Thread.sleep(delayMs)
                delayMs *= 2
            }
        }
    }

    fun setWebhook() {
        if (!isEnabled()) return
        val webhookUrl = telegramProperties.webhook.url.trim()
        val secretToken = telegramProperties.webhook.secretToken.trim()
        if (webhookUrl.isBlank() || secretToken.isBlank()) {
            logger.warn("[telegram] Skipping webhook registration because url or secret token is blank")
            return
        }

        restClient.post()
            .uri("/bot${telegramProperties.bot.token}/setWebhook")
            .body(
                mapOf(
                    "url" to webhookUrl,
                    "secret_token" to secretToken
                )
            )
            .retrieve()
            .toBodilessEntity()
        logger.info("[telegram] Webhook registration completed url={}", webhookUrl)
    }

    private fun isEnabled(): Boolean {
        return telegramProperties.integration.enabled && telegramProperties.bot.token.isNotBlank()
    }

    companion object {
        private const val MAX_SEND_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 300L
    }
}
