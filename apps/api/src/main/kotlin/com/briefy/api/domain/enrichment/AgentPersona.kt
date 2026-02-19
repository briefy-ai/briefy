package com.briefy.api.domain.enrichment

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "agent_personas")
class AgentPersona(
    @Id
    val id: UUID,

    @Column(name = "user_id")
    val userId: UUID?,

    @Column(name = "is_system", nullable = false)
    val isSystem: Boolean,

    @Enumerated(EnumType.STRING)
    @Column(name = "use_case", nullable = false, length = 40)
    val useCase: AgentPersonaUseCase,

    @Column(name = "name", nullable = false, length = 120)
    val name: String,

    @Column(name = "personality", nullable = false, length = 2000)
    val personality: String,

    @Column(name = "role", nullable = false, length = 2000)
    val role: String,

    @Column(name = "purpose", nullable = false, length = 2000)
    val purpose: String,

    @Column(name = "description", nullable = false, length = 2000)
    val description: String,

    @Column(name = "avatar_url", length = 2048)
    val avatarUrl: String?,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
)
