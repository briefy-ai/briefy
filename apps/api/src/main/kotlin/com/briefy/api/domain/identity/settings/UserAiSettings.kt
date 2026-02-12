package com.briefy.api.domain.identity.settings

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_ai_settings")
class UserAiSettings(
    @Id
    val id: UUID,

    @Column(name = "user_id", nullable = false, unique = true)
    val userId: UUID,

    @Column(name = "topic_extraction_provider", nullable = false, length = 50)
    var topicExtractionProvider: String,

    @Column(name = "topic_extraction_model", nullable = false, length = 100)
    var topicExtractionModel: String,

    @Column(name = "source_formatting_provider", nullable = false, length = 50)
    var sourceFormattingProvider: String,

    @Column(name = "source_formatting_model", nullable = false, length = 100)
    var sourceFormattingModel: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
