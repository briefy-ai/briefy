package com.briefy.api.domain.knowledgegraph.source.event

enum class SourceActivationReason {
    FRESH_EXTRACTION,
    CACHE_REUSE,
    MANUAL_CONTENT_OVERRIDE
}
