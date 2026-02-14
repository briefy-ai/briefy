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
        if (!telegramProperties.integration.enabled) return

        try {
            telegramBotGateway.setWebhook()
        } catch (e: Exception) {
            logger.error("[telegram] Failed to register webhook on startup", e)
        }
    }
}
