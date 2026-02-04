package com.briefy.api.domain.knowledgegraph.briefing

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "briefings")
class Briefing(
    @Id
    val id: UUID = UUID.randomUUID(),
)
