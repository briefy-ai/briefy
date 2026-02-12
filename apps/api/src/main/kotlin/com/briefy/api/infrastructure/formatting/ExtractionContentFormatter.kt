package com.briefy.api.infrastructure.formatting

import com.briefy.api.infrastructure.extraction.ExtractionProviderId

interface ExtractionContentFormatter {
    fun supports(extractorId: ExtractionProviderId): Boolean
    fun format(extractedContent: String, provider: String, model: String): String
}
