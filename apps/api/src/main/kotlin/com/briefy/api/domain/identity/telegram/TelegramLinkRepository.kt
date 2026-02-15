package com.briefy.api.domain.identity.telegram

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TelegramLinkRepository : JpaRepository<TelegramLink, UUID> {
    fun findByUserId(userId: UUID): TelegramLink?
    fun findByTelegramUserId(telegramUserId: Long): TelegramLink?
    fun deleteByUserId(userId: UUID): Long
    fun deleteByTelegramUserId(telegramUserId: Long): Long
}
