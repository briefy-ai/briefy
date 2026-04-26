package com.briefy.api.api.oauth

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class OAuthMetadataController(
    @Value("\${oauth.server.base-url:http://localhost:8080}") private val baseUrl: String
) {

    @GetMapping("/.well-known/oauth-authorization-server")
    fun metadata(): OAuthServerMetadata {
        return OAuthServerMetadata(
            issuer = baseUrl,
            authorizationEndpoint = "$baseUrl/oauth/authorize",
            tokenEndpoint = "$baseUrl/oauth/token",
            revocationEndpoint = "$baseUrl/oauth/revoke",
            responseTypesSupported = listOf("code"),
            grantTypesSupported = listOf("authorization_code", "refresh_token"),
            codeChallengeMethodsSupported = listOf("S256"),
            scopesSupported = listOf("mcp:read"),
            tokenEndpointAuthMethodsSupported = listOf("none")
        )
    }
}

data class OAuthServerMetadata(
    @JsonProperty("issuer") val issuer: String,
    @JsonProperty("authorization_endpoint") val authorizationEndpoint: String,
    @JsonProperty("token_endpoint") val tokenEndpoint: String,
    @JsonProperty("revocation_endpoint") val revocationEndpoint: String,
    @JsonProperty("response_types_supported") val responseTypesSupported: List<String>,
    @JsonProperty("grant_types_supported") val grantTypesSupported: List<String>,
    @JsonProperty("code_challenge_methods_supported") val codeChallengeMethodsSupported: List<String>,
    @JsonProperty("scopes_supported") val scopesSupported: List<String>,
    @JsonProperty("token_endpoint_auth_methods_supported") val tokenEndpointAuthMethodsSupported: List<String>
)
