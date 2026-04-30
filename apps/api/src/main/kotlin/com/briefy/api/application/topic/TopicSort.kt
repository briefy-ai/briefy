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

        fun fromOrNull(raw: String?): TopicSort? {
            val value = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            return entries.firstOrNull { it.value == value }
        }

        @JvmStatic
        @JsonCreator
        fun fromJson(raw: String): TopicSort {
            return fromOrNull(raw)
                ?: throw IllegalArgumentException("Invalid topic sort '$raw'. Expected one of: $valuesForPrompt.")
        }
    }

    @JsonValue
    fun toJson(): String = value
}
