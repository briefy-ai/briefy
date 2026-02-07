package com.briefy.api.domain.knowledgegraph.source

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "shared_source_snapshots")
class SharedSourceSnapshot(
    @Id
    val id: UUID,

    @Column(name = "url_normalized", nullable = false, length = 2048)
    val urlNormalized: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    val sourceType: SourceType,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    val status: SharedSourceSnapshotStatus = SharedSourceSnapshotStatus.ACTIVE,

    @Embedded
    val content: Content?,

    @Embedded
    val metadata: Metadata?,

    @Column(name = "fetched_at", nullable = false)
    val fetchedAt: Instant,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "version", nullable = false)
    val version: Int,

    @Column(name = "is_latest", nullable = false)
    var isLatest: Boolean,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

enum class SharedSourceSnapshotStatus {
    ACTIVE,
    FAILED
}
