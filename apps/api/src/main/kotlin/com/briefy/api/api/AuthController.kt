package com.briefy.api.api

import com.briefy.api.application.auth.*
import com.briefy.api.infrastructure.security.AuthCookieService
import com.briefy.api.infrastructure.security.CurrentUserProvider
import com.briefy.api.infrastructure.security.JwtService
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val jwtService: JwtService,
    private val authCookieService: AuthCookieService,
    private val currentUserProvider: CurrentUserProvider
) {

    @PostMapping("/signup")
    fun signUp(@Valid @RequestBody request: SignUpRequest): ResponseEntity<AuthUserResponse> {
        val result = authService.signUp(
            SignUpCommand(
                email = request.email,
                password = request.password,
                displayName = request.displayName
            )
        )

        return ResponseEntity.status(HttpStatus.CREATED)
            .header(HttpHeaders.SET_COOKIE, accessCookie(result.accessToken))
            .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken))
            .body(result.user)
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthUserResponse> {
        val result = authService.login(LoginCommand(email = request.email, password = request.password))
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, accessCookie(result.accessToken))
            .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken))
            .body(result.user)
    }

    @PostMapping("/refresh")
    fun refresh(
        @CookieValue(name = AuthCookieService.REFRESH_TOKEN_COOKIE, required = false) refreshToken: String?
    ): ResponseEntity<Map<String, Boolean>> {
        val refreshed = authService.refresh(refreshToken)
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, accessCookie(refreshed.accessToken))
            .body(mapOf("ok" to true))
    }

    @PostMapping("/logout")
    fun logout(
        @CookieValue(name = AuthCookieService.REFRESH_TOKEN_COOKIE, required = false) refreshToken: String?
    ): ResponseEntity<Void> {
        authService.logout(refreshToken)
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, authCookieService.clearAccessTokenCookie().toString())
            .header(HttpHeaders.SET_COOKIE, authCookieService.clearRefreshTokenCookie().toString())
            .build()
    }

    @GetMapping("/me")
    fun me(): ResponseEntity<AuthUserResponse> {
        val userId = currentUserProvider.requireUserId()
        return ResponseEntity.ok(authService.me(userId))
    }

    private fun accessCookie(accessToken: String): String {
        return authCookieService
            .createAccessTokenCookie(accessToken, jwtService.accessTokenMaxAgeSeconds())
            .toString()
    }

    private fun refreshCookie(refreshToken: String): String {
        return authCookieService.createRefreshTokenCookie(refreshToken).toString()
    }
}

data class SignUpRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    val password: String,

    @field:Size(max = 120, message = "Display name can have at most 120 characters")
    val displayName: String? = null
)

data class LoginRequest(
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    val email: String,

    @field:NotBlank(message = "Password is required")
    val password: String
)
