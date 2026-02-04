package com.briefy.api.domain.knowledgegraph.source

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "sources")
class Source(
    @Id
    val id: UUID = UUID.randomUUID(),
)
