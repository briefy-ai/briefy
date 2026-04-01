package com.briefy.api.infrastructure.ai

class RetryableToolExecutionException(
    val errorCode: String,
    override val message: String,
    val retryAfterSeconds: Long? = null
) : RuntimeException(message)
