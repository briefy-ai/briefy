package com.briefy.api.application.topic

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class TopicSort(val value: String) {
    MOST_FREQUENT("most_frequent"),
    MOST_RECENT("most_recent"),
    NEWLY_CREATED("newly_created"),
    OLDEST("oldest");

    companion object {
        val DEFAULT = MOST_RECENT
        val valuesForPrompt = entries.joinToString(", ") { it.value }

        @JvmStatic
        @JsonCreator
        fun from(raw: String?): TopicSort? {
            if (raw.isNullOrBlank()) {
                return DEFAULT
            }

            return when (raw.trim().lowercase().replace("-", "_")) {
                "most_frequent", "more_frequent", "frequent", "frequency", "source_count", "most_read" -> MOST_FREQUENT
                "most_recent", "recent", "recently_updated", "updated", "updated_desc" -> MOST_RECENT
                "newly_created", "newest", "new", "created_desc" -> NEWLY_CREATED
                "oldest", "older", "oldest_created", "created_asc" -> OLDEST
                else -> null
            }
        }
    }

    @JsonValue
    fun toJson(): String = value
}
