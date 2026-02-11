package com.briefy.api.domain.knowledgegraph.source.event

import java.time.Instant
import java.util.UUID

data class SourceActivatedEvent(
    val sourceId: UUID,
    val userId: UUID,
    val activationReason: SourceActivationReason,
    val occurredAt: Instant = Instant.now()
)
