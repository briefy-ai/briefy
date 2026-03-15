package com.briefy.api.infrastructure.tts

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "tts.inworld")
data class InworldTtsProperties(
    val baseUrl: String = "https://api.inworld.ai",
    val voiceId: String = "Ashley",
    val defaultModelId: String = "inworld-tts-1.5-mini",
    val maxCharactersPerRequest: Int = 2_000
)
