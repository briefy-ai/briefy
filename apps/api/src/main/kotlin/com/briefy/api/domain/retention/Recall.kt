package com.briefy.api.domain.retention

import jakarta.persistence.*
import java.util.UUID

@Entity
@Table(name = "recalls")
class Recall(
    @Id
    val id: UUID,
    @Column(name = "user_id", nullable = false)
    val userId: UUID,
)
