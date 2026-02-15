package com.briefy.api.application.telegram

import com.briefy.api.domain.identity.telegram.TelegramLink
import com.briefy.api.domain.identity.telegram.TelegramLinkCode
import com.briefy.api.domain.identity.telegram.TelegramLinkCodeRepository
import com.briefy.api.domain.identity.telegram.TelegramLinkRepository
import com.briefy.api.infrastructure.id.IdGenerator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Locale
import java.util.UUID

@Service
class TelegramLinkService(
    private val telegramLinkRepository: TelegramLinkRepository,
    private val telegramLinkCodeRepository: TelegramLinkCodeRepository,
    private val idGenerator: IdGenerator
) {
    private val secureRandom = SecureRandom()

    @Transactional(readOnly = true)
    fun getLinkStatus(userId: UUID): TelegramLinkStatusResponse {
        val link = telegramLinkRepository.findByUserId(userId)
        if (link == null) {
            return TelegramLinkStatusResponse(
                linked = false,
                telegramUsername = null,
                maskedTelegramId = null,
                linkedAt = null
            )
        }

        return TelegramLinkStatusResponse(
            linked = true,
            telegramUsername = link.telegramUsername,
            maskedTelegramId = maskTelegramUserId(link.telegramUserId),
            linkedAt = link.linkedAt
        )
    }

    @Transactional
    fun generateLinkCode(userId: UUID): TelegramLinkCodeResponse {
        telegramLinkCodeRepository.deleteByUserIdAndUsedAtIsNull(userId)
        val code = generateReadableCode()
        telegramLinkCodeRepository.save(
            TelegramLinkCode(
                id = idGenerator.newId(),
                userId = userId,
                codeHash = hash(code),
                createdAt = Instant.now()
            )
        )
        return TelegramLinkCodeResponse(
            code = code,
            expiresAt = null,
            instructions = "Send /link $code to the Telegram bot in a private chat."
        )
    }

    @Transactional(readOnly = true)
    fun findLinkByTelegramUserId(telegramUserId: Long): TelegramLink? {
        return telegramLinkRepository.findByTelegramUserId(telegramUserId)
    }

    @Transactional
    fun unlinkByUserId(userId: UUID): Boolean {
        return telegramLinkRepository.deleteByUserId(userId) > 0
    }

    @Transactional
    fun unlinkByTelegramUserId(telegramUserId: Long): Boolean {
        return telegramLinkRepository.deleteByTelegramUserId(telegramUserId) > 0
    }

    @Transactional
    fun linkTelegramAccount(
        telegramUserId: Long,
        telegramChatId: Long,
        telegramUsername: String?,
        rawCode: String
    ): TelegramLink {
        val normalizedCode = rawCode.trim().uppercase(Locale.ROOT)
        val code = telegramLinkCodeRepository.findByCodeHashAndUsedAtIsNull(hash(normalizedCode))
            ?: throw IllegalArgumentException("Invalid link code. Generate a new code in Briefy settings.")

        val now = Instant.now()
        if (code.expiresAt != null && now.isAfter(code.expiresAt)) {
            throw IllegalArgumentException("Link code expired. Generate a new code in Briefy settings.")
        }

        val existingByTelegram = telegramLinkRepository.findByTelegramUserId(telegramUserId)
        if (existingByTelegram != null && existingByTelegram.userId != code.userId) {
            throw IllegalStateException("This Telegram account is already linked to another Briefy user.")
        }

        val existingByUser = telegramLinkRepository.findByUserId(code.userId)
        if (existingByUser != null && existingByUser.telegramUserId != telegramUserId) {
            throw IllegalStateException("This Briefy account is already linked to another Telegram account.")
        }

        val link = existingByTelegram ?: existingByUser ?: TelegramLink(
            id = idGenerator.newId(),
            userId = code.userId,
            telegramUserId = telegramUserId,
            telegramChatId = telegramChatId,
            telegramUsername = telegramUsername,
            linkedAt = now,
            updatedAt = now
        )
        link.telegramUserId = telegramUserId
        link.telegramChatId = telegramChatId
        link.telegramUsername = telegramUsername
        link.updatedAt = now
        code.usedAt = now
        telegramLinkCodeRepository.save(code)
        return telegramLinkRepository.save(link)
    }

    private fun hash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun generateReadableCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString {
            repeat(8) {
                append(alphabet[secureRandom.nextInt(alphabet.length)])
            }
        }
    }

    private fun maskTelegramUserId(userId: Long): String {
        val text = userId.toString()
        return if (text.length <= 4) "***$text" else "${"*".repeat(text.length - 4)}${text.takeLast(4)}"
    }
}
