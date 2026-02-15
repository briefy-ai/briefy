package com.briefy.api.infrastructure.telegram

import com.briefy.api.config.TelegramProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

@Component
class TelegramWebhookRegistrar(
    private val telegramBotGateway: TelegramBotGateway,
    private val telegramProperties: TelegramProperties
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(TelegramWebhookRegistrar::class.java)

    override fun run(args: ApplicationArguments) {
        logger.info(
            "[telegram] startup integrationEnabled={} botUsername={} webhookUrlConfigured={} secretConfigured={} tokenConfigured={}",
            telegramProperties.integration.enabled,
            telegramProperties.bot.username.ifBlank { "n/a" },
            telegramProperties.webhook.url.isNotBlank(),
            telegramProperties.webhook.secretToken.isNotBlank(),
            telegramProperties.bot.token.isNotBlank()
        )
        if (!telegramProperties.integration.enabled) {
            logger.info("[telegram] startup skipped webhook registration because integration is disabled")
            return
        }

        try {
            logger.info("[telegram] startup registering webhook")
            telegramBotGateway.setWebhook()
            logger.info("[telegram] startup webhook registration flow completed")
        } catch (e: Exception) {
            logger.error("[telegram] Failed to register webhook on startup", e)
        }
    }
}
