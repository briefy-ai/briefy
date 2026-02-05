package com.briefy.api.infrastructure.extraction

interface ContentExtractor {
    fun extract(url: String): ExtractionResult
}
