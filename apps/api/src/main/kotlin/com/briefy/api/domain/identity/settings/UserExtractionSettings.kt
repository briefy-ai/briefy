package com.briefy.api.domain.identity.settings

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_extraction_settings")
class UserExtractionSettings(
    @Id
    val id: UUID,

    @Column(name = "user_id", nullable = false, unique = true)
    val userId: UUID,

    @Column(name = "firecrawl_enabled", nullable = false)
    var firecrawlEnabled: Boolean = false,

    @Column(name = "firecrawl_api_key_encrypted", length = 1024)
    var firecrawlApiKeyEncrypted: String? = null,

    @Column(name = "x_api_enabled", nullable = false)
    var xApiEnabled: Boolean = false,

    @Column(name = "x_api_bearer_token_encrypted", length = 1024)
    var xApiBearerTokenEncrypted: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)
