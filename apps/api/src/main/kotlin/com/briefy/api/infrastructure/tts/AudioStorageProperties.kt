package com.briefy.api.infrastructure.tts

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "audio.storage")
data class AudioStorageProperties(
    val endpoint: String = "http://localhost:9000",
    val accessKeyId: String = "minioadmin",
    val secretAccessKey: String = "minioadmin",
    val bucket: String = "briefy-audio",
    val region: String = "us-east-1",
    val presignedUrlExpiryHours: Long = 24
)
