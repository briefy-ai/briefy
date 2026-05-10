package com.briefy.api.domain.identity.session

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface RefreshSessionRepository : JpaRepository<RefreshSession, UUID> {
    fun findByTokenHashAndRevokedAtIsNull(tokenHash: String): RefreshSession?
    fun findByTokenHash(tokenHash: String): RefreshSession?
}
