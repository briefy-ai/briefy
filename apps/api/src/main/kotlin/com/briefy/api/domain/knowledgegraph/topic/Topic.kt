package com.briefy.api.domain.knowledgegraph.topic

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "topics")
class Topic(
    @Id
    val id: UUID = UUID.randomUUID(),
)
