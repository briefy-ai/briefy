package com.briefy.api.application.auth

data class SignUpCommand(
    val email: String,
    val password: String,
    val displayName: String?
)

data class LoginCommand(
    val email: String,
    val password: String
)
