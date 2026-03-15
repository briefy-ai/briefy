package com.briefy.api.infrastructure.tts

import org.springframework.stereotype.Component

@Component
class TtsVoiceResolver(
    private val elevenLabsProperties: ElevenLabsTtsProperties,
    private val inworldProperties: InworldTtsProperties
) {
    fun resolveVoiceId(providerType: TtsProviderType, languageCode: String): String {
        return when (providerType) {
            TtsProviderType.ELEVENLABS -> elevenLabsProperties.voiceId
            TtsProviderType.INWORLD -> when (languageCode.lowercase()) {
                "es" -> inworldProperties.spanishVoiceId
                else -> inworldProperties.englishVoiceId
            }
        }
    }
}
