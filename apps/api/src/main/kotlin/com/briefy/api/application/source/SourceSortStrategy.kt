package com.briefy.api.application.source

enum class SourceSortStrategy(val paramValue: String) {
    NEWEST("newest"),
    OLDEST("oldest"),
    LONGEST_READ("longest"),
    SHORTEST_READ("shortest");

    companion object {
        fun fromParam(value: String?): SourceSortStrategy {
            if (value.isNullOrBlank()) {
                return NEWEST
            }
            return entries.firstOrNull { it.paramValue == value.trim().lowercase() }
                ?: throw IllegalArgumentException("Invalid sort")
        }
    }
}
