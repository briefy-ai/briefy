package com.briefy.api.infrastructure.tts

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "tts.elevenlabs")
data class TtsProperties(
    val baseUrl: String = "https://api.elevenlabs.io",
    val voiceId: String = "iiidtqDt9FBdT1vfBluA",
    val modelId: String = "eleven_flash_v2_5",
    val maxCharacters: Int = 100_000
)
