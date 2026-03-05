package com.briefy.api.application.briefing

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

data class RunEventPageCursor(
    val occurredAt: Instant,
    val sequenceId: Long
)

object RunEventPageCursorCodec {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(cursor: RunEventPageCursor): String {
        val raw = "${cursor.occurredAt}|${cursor.sequenceId}"
        return encoder.encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
    }

    fun decode(value: String): RunEventPageCursor {
        val decoded = try {
            String(decoder.decode(value), StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid cursor")
        }

        val parts = decoded.split("|", limit = 2)
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid cursor")
        }

        val occurredAt = try {
            Instant.parse(parts[0])
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid cursor")
        }

        val sequenceId = try {
            parts[1].toLong()
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid cursor")
        }

        return RunEventPageCursor(occurredAt = occurredAt, sequenceId = sequenceId)
    }
}
