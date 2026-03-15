package com.briefy.api.infrastructure.tts

class ElevenLabsTtsException(
    val code: String,
    val userMessage: String,
    val retryable: Boolean,
    cause: Throwable? = null
) : RuntimeException(userMessage, cause)
