package com.briefy.api.infrastructure.tts

class InworldTtsException(
    val code: String,
    val userMessage: String,
    val retryable: Boolean,
    cause: Throwable? = null
) : RuntimeException(userMessage, cause)
