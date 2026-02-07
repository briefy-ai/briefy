package com.briefy.api.domain.knowledgegraph.source

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SourceRepository : JpaRepository<Source, UUID> {
    fun findByUserIdAndUrlNormalized(userId: UUID, normalizedUrl: String): Source?
    fun findByUserIdAndStatus(userId: UUID, status: SourceStatus): List<Source>
    fun findByUserId(userId: UUID): List<Source>
    fun findByIdAndUserId(id: UUID, userId: UUID): Source?
    fun countByUrlNormalized(normalizedUrl: String): Long
}
