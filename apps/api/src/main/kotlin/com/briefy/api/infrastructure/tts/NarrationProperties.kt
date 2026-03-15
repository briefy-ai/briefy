package com.briefy.api.infrastructure.tts

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "tts")
data class NarrationProperties(
    val maxCharacters: Int = 100_000
)
