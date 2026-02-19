package com.briefy.api.domain.knowledgegraph.briefing

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "briefing_sources")
class BriefingSource(
    @Id
    val id: UUID,

    @Column(name = "briefing_id", nullable = false)
    val briefingId: UUID,

    @Column(name = "source_id", nullable = false)
    val sourceId: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
)
