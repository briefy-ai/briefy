package com.briefy.api.domain.knowledgegraph.source.event

import java.time.Instant
import java.util.UUID

data class SourceArchivedEvent(
    val sourceId: UUID,
    val userId: UUID,
    val occurredAt: Instant = Instant.now()
)
