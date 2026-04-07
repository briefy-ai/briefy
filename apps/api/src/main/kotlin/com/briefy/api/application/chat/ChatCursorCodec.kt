package com.briefy.api.application.chat

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.UUID

data class ConversationListCursor(
    val updatedAt: Instant,
    val id: UUID
)

object ConversationListCursorCodec {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(cursor: ConversationListCursor): String {
        val raw = "${cursor.updatedAt}|${cursor.id}"
        return encoder.encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
    }

    fun decode(value: String): ConversationListCursor {
        val decoded = try {
            String(decoder.decode(value), StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid cursor")
        }

        val parts = decoded.split("|", limit = 2)
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid cursor")
        }

        val updatedAt = try {
            Instant.parse(parts[0])
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid cursor")
        }

        val id = try {
            UUID.fromString(parts[1])
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid cursor")
        }

        return ConversationListCursor(updatedAt = updatedAt, id = id)
    }
}
