package com.briefy.api.domain.sharing

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ShareLinkRepository : JpaRepository<ShareLink, UUID> {
    fun findByToken(token: String): ShareLink?
    fun findByUserIdAndEntityTypeAndEntityId(userId: UUID, entityType: ShareLinkEntityType, entityId: UUID): List<ShareLink>
}
