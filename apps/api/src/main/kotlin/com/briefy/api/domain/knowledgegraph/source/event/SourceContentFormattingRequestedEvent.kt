package com.briefy.api.domain.knowledgegraph.source.event

import com.briefy.api.infrastructure.extraction.ExtractionProviderId
import java.time.Instant
import java.util.UUID

data class SourceContentFormattingRequestedEvent(
    val sourceId: UUID,
    val userId: UUID,
    val extractorId: ExtractionProviderId,
    val occurredAt: Instant = Instant.now()
)
