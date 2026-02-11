package com.briefy.api.domain.knowledgegraph.topic

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TopicRepository : JpaRepository<Topic, UUID> {
    fun findByIdAndUserId(id: UUID, userId: UUID): Topic?
    fun findByUserIdAndNameNormalized(userId: UUID, nameNormalized: String): Topic?
    fun findAllByIdInAndUserId(ids: Collection<UUID>, userId: UUID): List<Topic>
    fun findByUserIdAndStatusOrderByUpdatedAtDesc(userId: UUID, status: TopicStatus): List<Topic>
    fun findByUserIdAndStatusAndNameContainingIgnoreCaseOrderByUpdatedAtDesc(
        userId: UUID,
        status: TopicStatus,
        name: String
    ): List<Topic>
}
