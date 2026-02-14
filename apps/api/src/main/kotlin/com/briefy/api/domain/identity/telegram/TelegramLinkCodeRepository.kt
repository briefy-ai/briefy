package com.briefy.api.domain.identity.telegram

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TelegramLinkCodeRepository : JpaRepository<TelegramLinkCode, UUID> {
    fun findByCodeHashAndUsedAtIsNull(codeHash: String): TelegramLinkCode?
    fun deleteByUserIdAndUsedAtIsNull(userId: UUID): Long
}
