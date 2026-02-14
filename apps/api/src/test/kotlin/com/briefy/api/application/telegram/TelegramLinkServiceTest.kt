package com.briefy.api.application.telegram

import com.briefy.api.domain.identity.telegram.TelegramLink
import com.briefy.api.domain.identity.telegram.TelegramLinkCode
import com.briefy.api.domain.identity.telegram.TelegramLinkCodeRepository
import com.briefy.api.domain.identity.telegram.TelegramLinkRepository
import com.briefy.api.infrastructure.id.IdGenerator
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class TelegramLinkServiceTest {
    private val linkRepository: TelegramLinkRepository = mock()
    private val codeRepository: TelegramLinkCodeRepository = mock()
    private val idGenerator: IdGenerator = mock()
    private val service = TelegramLinkService(linkRepository, codeRepository, idGenerator)

    @Test
    fun `generateLinkCode should rotate previous active code`() {
        whenever(idGenerator.newId()).thenReturn(UUID.randomUUID())

        service.generateLinkCode(USER_ID)

        verify(codeRepository).deleteByUserIdAndUsedAtIsNull(USER_ID)
        verify(codeRepository).save(any())
    }

    @Test
    fun `linkTelegramAccount should persist link for valid code`() {
        val code = TelegramLinkCode(
            id = UUID.randomUUID(),
            userId = USER_ID,
            codeHash = "hash",
            createdAt = Instant.now(),
            usedAt = null
        )
        val linkId = UUID.randomUUID()
        whenever(idGenerator.newId()).thenReturn(linkId)
        whenever(codeRepository.findByCodeHashAndUsedAtIsNull(any())).thenReturn(code)
        whenever(linkRepository.findByTelegramUserId(eq(TELEGRAM_USER_ID))).thenReturn(null)
        whenever(linkRepository.findByUserId(eq(USER_ID))).thenReturn(null)
        whenever(linkRepository.save(any())).thenAnswer { it.arguments.first() as TelegramLink }

        val result = service.linkTelegramAccount(
            telegramUserId = TELEGRAM_USER_ID,
            telegramChatId = TELEGRAM_CHAT_ID,
            telegramUsername = "briefy_user",
            rawCode = "ABCD1234"
        )

        assertNotNull(code.usedAt)
        verify(codeRepository).save(code)
        verify(linkRepository).save(any())
        org.junit.jupiter.api.Assertions.assertEquals(USER_ID, result.userId)
        org.junit.jupiter.api.Assertions.assertEquals(TELEGRAM_USER_ID, result.telegramUserId)
    }

    @Test
    fun `linkTelegramAccount should reject when telegram account already linked to another user`() {
        val code = TelegramLinkCode(
            id = UUID.randomUUID(),
            userId = USER_ID,
            codeHash = "hash",
            createdAt = Instant.now(),
            usedAt = null
        )
        whenever(codeRepository.findByCodeHashAndUsedAtIsNull(any())).thenReturn(code)
        whenever(linkRepository.findByTelegramUserId(eq(TELEGRAM_USER_ID))).thenReturn(
            TelegramLink(
                id = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                telegramUserId = TELEGRAM_USER_ID,
                telegramChatId = TELEGRAM_CHAT_ID
            )
        )

        assertThrows(IllegalStateException::class.java) {
            service.linkTelegramAccount(
                telegramUserId = TELEGRAM_USER_ID,
                telegramChatId = TELEGRAM_CHAT_ID,
                telegramUsername = null,
                rawCode = "ABCD1234"
            )
        }
    }

    companion object {
        private val USER_ID: UUID = UUID.randomUUID()
        private const val TELEGRAM_USER_ID = 12345L
        private const val TELEGRAM_CHAT_ID = 12345L
    }
}
