package com.briefy.api.domain.identity.oauthserver

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface OAuthClientRepository : JpaRepository<OAuthClient, UUID> {
    fun findByClientId(clientId: String): OAuthClient?
}
