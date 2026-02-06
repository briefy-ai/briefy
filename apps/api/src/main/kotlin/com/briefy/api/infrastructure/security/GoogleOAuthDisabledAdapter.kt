package com.briefy.api.infrastructure.security

import com.briefy.api.domain.identity.oauth.OAuthIdentityPort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class GoogleOAuthDisabledAdapter(
    @Value("\${google.auth.enabled:false}") private val enabled: Boolean
) : OAuthIdentityPort {
    override fun isGoogleEnabled(): Boolean = enabled
}
