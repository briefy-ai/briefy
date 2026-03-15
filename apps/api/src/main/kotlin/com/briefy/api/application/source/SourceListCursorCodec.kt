package com.briefy.api.application.source

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.UUID

data class SourceListCursor(
    val sortStrategy: SourceSortStrategy,
    val id: UUID,
    val instantValue: Instant? = null,
    val readingTime: Int? = null
)

object SourceListCursorCodec {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(cursor: SourceListCursor): String {
        val raw = when (cursor.sortStrategy) {
            SourceSortStrategy.NEWEST -> "n|${requireNotNull(cursor.instantValue)}|${cursor.id}"
            SourceSortStrategy.OLDEST -> "c|${requireNotNull(cursor.instantValue)}|${cursor.id}"
            SourceSortStrategy.LONGEST_READ -> "r|${cursor.readingTime?.toString().orEmpty()}|${cursor.id}"
            SourceSortStrategy.SHORTEST_READ -> "rs|${cursor.readingTime?.toString().orEmpty()}|${cursor.id}"
        }
        return encoder.encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
    }

    fun decode(value: String, requestedSortStrategy: SourceSortStrategy): SourceListCursor {
        val decoded = try {
            String(decoder.decode(value), StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid cursor")
        }
        val parts = decoded.split("|", limit = 3)
        if (parts.size != 3) {
            throw IllegalArgumentException("Invalid cursor")
        }

        val sortStrategy = when (parts[0]) {
            "n" -> SourceSortStrategy.NEWEST
            "c" -> SourceSortStrategy.OLDEST
            "r" -> SourceSortStrategy.LONGEST_READ
            "rs" -> SourceSortStrategy.SHORTEST_READ
            else -> throw IllegalArgumentException("Invalid cursor")
        }
        if (sortStrategy != requestedSortStrategy) {
            throw IllegalArgumentException("Invalid cursor")
        }

        val id = try {
            UUID.fromString(parts[2])
        } catch (_: Exception) {
            throw IllegalArgumentException("Invalid cursor")
        }

        return when (sortStrategy) {
            SourceSortStrategy.NEWEST,
            SourceSortStrategy.OLDEST -> {
                val instantValue = try {
                    Instant.parse(parts[1])
                } catch (_: Exception) {
                    throw IllegalArgumentException("Invalid cursor")
                }
                SourceListCursor(sortStrategy = sortStrategy, id = id, instantValue = instantValue)
            }

            SourceSortStrategy.LONGEST_READ,
            SourceSortStrategy.SHORTEST_READ -> {
                val readingTime = parts[1].takeIf { it.isNotBlank() }?.toIntOrNull()
                    ?: if (parts[1].isBlank()) null else throw IllegalArgumentException("Invalid cursor")
                SourceListCursor(sortStrategy = sortStrategy, id = id, readingTime = readingTime)
            }
        }
    }
}
