package com.briefy.api.domain.knowledgegraph.source

enum class SourceType {
    NEWS,
    BLOG,
    RESEARCH,
    VIDEO;

    fun ttlSeconds(): Long {
        return when (this) {
            NEWS -> 24 * 60 * 60L
            BLOG -> 7 * 24 * 60 * 60L
            RESEARCH -> 30 * 24 * 60 * 60L
            VIDEO -> 30 * 24 * 60 * 60L
        }
    }
}
