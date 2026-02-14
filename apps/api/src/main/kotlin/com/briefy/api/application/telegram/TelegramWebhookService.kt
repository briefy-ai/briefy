package com.briefy.api.application.telegram

import com.briefy.api.domain.identity.telegram.TelegramLink
import com.briefy.api.infrastructure.telegram.TelegramBotGateway
import com.briefy.api.infrastructure.telegram.TelegramUrlExtractor
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import org.telegram.telegrambots.meta.api.objects.Update

@Service
class TelegramWebhookService(
    private val telegramLinkService: TelegramLinkService,
    private val telegramIngestionJobService: TelegramIngestionJobService,
    private val telegramBotGateway: TelegramBotGateway,
    private val telegramUrlExtractor: TelegramUrlExtractor
) {
    private val logger = LoggerFactory.getLogger(TelegramWebhookService::class.java)

    fun handleUpdate(update: Update) {
        val message = update.message ?: return
        val chat = message.chat ?: return
        val from = message.from ?: return
        val chatType = chat.type ?: ""
        if (!chatType.equals("private", ignoreCase = true)) return

        val text = message.text ?: message.caption ?: ""
        if (text.isBlank()) return

        if (text.startsWith("/")) {
            handleCommand(
                telegramUserId = from.id,
                telegramChatId = chat.id,
                username = from.userName,
                rawText = text
            )
            return
        }

        val extracted = telegramUrlExtractor.extract(text, MAX_URLS_PER_MESSAGE)
        if (extracted.urls.isEmpty()) {
            return
        }

        val link = telegramLinkService.findLinkByTelegramUserId(from.id)
        if (link == null) {
            sendSafely(
                chat.id,
                "Your Telegram account is not linked yet. Open Briefy settings, generate a link code, then send /link CODE."
            )
            return
        }

        telegramIngestionJobService.enqueue(
            telegramChatId = chat.id,
            telegramMessageId = message.messageId.toLong(),
            telegramUserId = from.id,
            linkedUserId = link.userId,
            payloadText = text,
            now = Instant.now()
        )
        val truncatedText = if (extracted.truncated) " (processing first $MAX_URLS_PER_MESSAGE URLs)" else ""
        sendSafely(chat.id, "Received ${extracted.urls.size} URL(s). Processing now$truncatedText.")
    }

    private fun handleCommand(
        telegramUserId: Long,
        telegramChatId: Long,
        username: String?,
        rawText: String
    ) {
        val parts = rawText.trim().split(Regex("\\s+"), limit = 2)
        val command = parts.firstOrNull()?.lowercase()?.substringBefore("@").orEmpty()
        when (command) {
            "/start", "/help" -> sendSafely(telegramChatId, helpText())
            "/link" -> {
                val code = parts.getOrNull(1)?.trim().orEmpty()
                if (code.isBlank()) {
                    sendSafely(
                        telegramChatId,
                        "Missing link code. Open Briefy settings and send /link CODE."
                    )
                    return
                }
                try {
                    telegramLinkService.linkTelegramAccount(
                        telegramUserId = telegramUserId,
                        telegramChatId = telegramChatId,
                        telegramUsername = username,
                        rawCode = code
                    )
                    sendSafely(telegramChatId, "Linked successfully. Send me any message with URLs.")
                } catch (e: Exception) {
                    logger.warn("[telegram] link command failed telegramUserId={}", telegramUserId, e)
                    sendSafely(telegramChatId, e.message ?: "Link failed.")
                }
            }

            "/unlink" -> {
                val removed = telegramLinkService.unlinkByTelegramUserId(telegramUserId)
                val response = if (removed) {
                    "Telegram account unlinked from Briefy."
                } else {
                    "No active Briefy link found for this Telegram account."
                }
                sendSafely(telegramChatId, response)
            }

            else -> sendSafely(telegramChatId, helpText())
        }
    }

    private fun sendSafely(chatId: Long, text: String) {
        try {
            telegramBotGateway.sendMessage(chatId, text)
        } catch (e: Exception) {
            logger.warn("[telegram] sendMessage failed chatId={}", chatId, e)
        }
    }

    private fun helpText(): String {
        return buildString {
            appendLine("Briefy Telegram Bot")
            appendLine("- Send any message containing URLs to ingest sources.")
            appendLine("- /link CODE links this Telegram account to your Briefy user.")
            appendLine("- /unlink removes your current link.")
            append("- /help shows this message.")
        }
    }

    companion object {
        private const val MAX_URLS_PER_MESSAGE = 10
    }
}
