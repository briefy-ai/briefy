package com.briefy.api.domain.knowledgegraph.source

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SourceRepository : JpaRepository<Source, UUID> {
    fun findByUrlNormalized(normalizedUrl: String): Source?
    fun findByStatus(status: SourceStatus): List<Source>
    fun findByOwnerId(ownerId: UUID): List<Source>
    fun findByOwnerIdAndStatus(ownerId: UUID, status: SourceStatus): List<Source>
}
