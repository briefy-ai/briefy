package com.briefy.api.application.auth

class EmailAlreadyExistsException(email: String) : RuntimeException("An account with email '$email' already exists")

class InvalidCredentialsException : RuntimeException("Invalid email or password")

class UnauthorizedException(message: String = "Unauthorized") : RuntimeException(message)

class UserNotFoundException : RuntimeException("User not found")
