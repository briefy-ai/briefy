package com.briefy.api.application.telegram

import java.time.Instant

data class TelegramLinkStatusResponse(
    val linked: Boolean,
    val telegramUsername: String?,
    val maskedTelegramId: String?,
    val linkedAt: Instant?
)

data class TelegramLinkCodeResponse(
    val code: String,
    val expiresAt: Instant?,
    val instructions: String
)
