package com.briefy.api.domain.enrichment

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "enrichments")
class Enrichment(
    @Id
    val id: UUID = UUID.randomUUID(),
)
