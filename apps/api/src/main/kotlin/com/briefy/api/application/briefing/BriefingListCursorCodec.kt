package com.briefy.api.application.briefing

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.UUID

data class BriefingListCursor(
    val createdAt: Instant,
    val id: UUID
)

object BriefingListCursorCodec {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(cursor: BriefingListCursor): String {
        val raw = "${cursor.createdAt}|${cursor.id}"
        return encoder.encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
    }

    fun decode(value: String): BriefingListCursor {
        val decoded = try {
            String(decoder.decode(value), StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid cursor")
        }
        val parts = decoded.split("|", limit = 2)
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid cursor")
        }
        val createdAt = try {
            Instant.parse(parts[0])
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid cursor")
        }
        val id = try {
            UUID.fromString(parts[1])
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid cursor")
        }
        return BriefingListCursor(createdAt = createdAt, id = id)
    }
}
