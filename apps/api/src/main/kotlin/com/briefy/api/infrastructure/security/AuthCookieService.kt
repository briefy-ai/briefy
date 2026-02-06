package com.briefy.api.infrastructure.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class AuthCookieService(
    @Value("\${auth.cookie.secure:false}") private val secure: Boolean,
    @Value("\${auth.cookie.same-site:Lax}") private val sameSite: String,
    @Value("\${auth.refresh-token.days:30}") private val refreshTokenDays: Long
) {
    fun createAccessTokenCookie(token: String, maxAgeSeconds: Long): ResponseCookie {
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE, token)
            .httpOnly(true)
            .secure(secure)
            .sameSite(sameSite)
            .path("/")
            .maxAge(Duration.ofSeconds(maxAgeSeconds))
            .build()
    }

    fun createRefreshTokenCookie(token: String): ResponseCookie {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, token)
            .httpOnly(true)
            .secure(secure)
            .sameSite(sameSite)
            .path("/api/auth")
            .maxAge(Duration.ofDays(refreshTokenDays))
            .build()
    }

    fun clearAccessTokenCookie(): ResponseCookie {
        return ResponseCookie.from(ACCESS_TOKEN_COOKIE, "")
            .httpOnly(true)
            .secure(secure)
            .sameSite(sameSite)
            .path("/")
            .maxAge(Duration.ZERO)
            .build()
    }

    fun clearRefreshTokenCookie(): ResponseCookie {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
            .httpOnly(true)
            .secure(secure)
            .sameSite(sameSite)
            .path("/api/auth")
            .maxAge(Duration.ZERO)
            .build()
    }

    fun refreshTokenTtlSeconds(): Long = Duration.ofDays(refreshTokenDays).seconds

    companion object {
        const val ACCESS_TOKEN_COOKIE = "briefy_access_token"
        const val REFRESH_TOKEN_COOKIE = "briefy_refresh_token"
    }
}
