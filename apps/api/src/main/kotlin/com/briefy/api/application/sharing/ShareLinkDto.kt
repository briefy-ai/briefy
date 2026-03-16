package com.briefy.api.application.sharing

import com.briefy.api.domain.sharing.ShareLink
import com.briefy.api.domain.sharing.ShareLinkEntityType
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class CreateShareLinkRequest(
    @field:NotNull(message = "entityType is required")
    val entityType: ShareLinkEntityType,
    @field:NotNull(message = "entityId is required")
    val entityId: UUID,
    val expiresAt: Instant? = null,
    val generateCoverImage: Boolean = false
)

data class ShareLinkResponse(
    val id: UUID,
    val token: String,
    val entityType: ShareLinkEntityType,
    val entityId: UUID,
    val expiresAt: Instant?,
    val createdAt: Instant
)

data class SharedSourceData(
    val title: String?,
    val url: String,
    val sourceType: String,
    val coverImageUrl: String?,
    val author: String?,
    val publishedDate: Instant?,
    val readingTimeMinutes: Int?,
    val content: String?,
    val audio: SharedSourceAudioData?
)

data class SharedSourceAudioData(
    val audioUrl: String,
    val durationSeconds: Int,
    val format: String
)

data class SharedSourceResponse(
    val entityType: ShareLinkEntityType,
    val expiresAt: Instant?,
    val source: SharedSourceData
)

data class ShareLinkAudioResponse(
    val audioUrl: String
)

fun ShareLink.toResponse() = ShareLinkResponse(
    id = id,
    token = token,
    entityType = entityType,
    entityId = entityId,
    expiresAt = expiresAt,
    createdAt = createdAt
)
