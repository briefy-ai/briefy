package com.briefy.api.domain.identity.oauthserver

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface OAuthAccessGrantRepository : JpaRepository<OAuthAccessGrant, UUID> {
    fun findByRefreshTokenHash(refreshTokenHash: String): OAuthAccessGrant?
    fun findByUserIdAndClientIdAndRevokedAtIsNull(userId: UUID, clientId: String): List<OAuthAccessGrant>

    @Query(
        """
        select grant
        from OAuthAccessGrant grant
        where grant.userId = :userId
          and grant.revokedAt is null
          and grant.expiresAt > :now
        """
    )
    fun findActiveByUserId(userId: UUID, now: Instant): List<OAuthAccessGrant>
}
