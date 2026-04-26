package com.briefy.api.domain.identity.oauthserver

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface OAuthAccessGrantRepository : JpaRepository<OAuthAccessGrant, UUID> {
    fun findByRefreshTokenHash(refreshTokenHash: String): OAuthAccessGrant?
    fun findByUserIdAndClientIdAndRevokedAtIsNull(userId: UUID, clientId: String): OAuthAccessGrant?
    fun findByUserIdAndRevokedAtIsNull(userId: UUID): List<OAuthAccessGrant>
}
