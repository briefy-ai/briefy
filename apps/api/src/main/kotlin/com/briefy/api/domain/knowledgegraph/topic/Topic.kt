package com.briefy.api.domain.knowledgegraph.topic

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "topics")
class Topic(
    @Id
    val id: UUID,
    @Column(name = "user_id", nullable = false)
    val userId: UUID,
)
