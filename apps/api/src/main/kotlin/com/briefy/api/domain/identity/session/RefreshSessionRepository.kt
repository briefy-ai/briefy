package com.briefy.api.domain.identity.session

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface RefreshSessionRepository : JpaRepository<RefreshSession, UUID> {
    fun findByTokenHashAndRevokedAtIsNull(tokenHash: String): RefreshSession?

    @Modifying
    @Query(
        """
        update RefreshSession s
        set s.revokedAt = :revokedAt
        where s.userId = :userId and s.revokedAt is null
        """
    )
    fun revokeActiveByUserId(
        @Param("userId") userId: UUID,
        @Param("revokedAt") revokedAt: Instant
    ): Int
}
