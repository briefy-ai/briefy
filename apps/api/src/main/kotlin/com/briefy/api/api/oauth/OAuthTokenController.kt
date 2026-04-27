package com.briefy.api.api.oauth

import com.briefy.api.application.oauthserver.OAuthInvalidRequestException
import com.briefy.api.application.oauthserver.OAuthServerService
import com.briefy.api.application.oauthserver.TokenResponse
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class OAuthTokenController(
    private val oauthServerService: OAuthServerService
) {
    private val logger = LoggerFactory.getLogger(OAuthTokenController::class.java)

    @PostMapping(value = ["/oauth/token", "/token"], consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun token(
        @RequestParam("grant_type") grantType: String,
        @RequestParam("client_id") clientId: String,
        @RequestParam("code", required = false) code: String?,
        @RequestParam("redirect_uri", required = false) redirectUri: String?,
        @RequestParam("code_verifier", required = false) codeVerifier: String?,
        @RequestParam("refresh_token", required = false) refreshToken: String?
    ): ResponseEntity<OAuthTokenApiResponse> {
        logger.info("[oauth] Token request grant_type={} clientId={}", grantType, clientId)

        return when (grantType) {
            "authorization_code" -> {
                if (code == null) throw OAuthInvalidRequestException("code is required")
                if (redirectUri == null) throw OAuthInvalidRequestException("redirect_uri is required")
                if (codeVerifier == null) throw OAuthInvalidRequestException("code_verifier is required")
                val result = oauthServerService.exchangeAuthorizationCode(code, clientId, redirectUri, codeVerifier)
                ResponseEntity.ok(result.toApiResponse())
            }
            "refresh_token" -> {
                if (refreshToken == null) throw OAuthInvalidRequestException("refresh_token is required")
                val result = oauthServerService.refreshAccessToken(refreshToken, clientId)
                ResponseEntity.ok(result.toApiResponse())
            }
            else -> throw com.briefy.api.application.oauthserver.OAuthUnsupportedGrantTypeException(grantType)
        }
    }

    @PostMapping(value = ["/oauth/revoke", "/revoke"], consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun revoke(
        @RequestParam("token") token: String
    ): ResponseEntity<Void> {
        logger.info("[oauth] Revoke request received")
        oauthServerService.revokeByRefreshToken(token)
        return ResponseEntity.ok().build()
    }

    private fun TokenResponse.toApiResponse() = OAuthTokenApiResponse(
        accessToken = accessToken,
        tokenType = tokenType,
        expiresIn = expiresIn,
        refreshToken = refreshToken,
        scope = scope
    )
}

data class OAuthTokenApiResponse(
    @JsonProperty("access_token") val accessToken: String,
    @JsonProperty("token_type") val tokenType: String,
    @JsonProperty("expires_in") val expiresIn: Long,
    @JsonProperty("refresh_token") val refreshToken: String?,
    @JsonProperty("scope") val scope: String
)
