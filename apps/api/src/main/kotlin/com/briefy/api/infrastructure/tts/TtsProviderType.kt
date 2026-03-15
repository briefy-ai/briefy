package com.briefy.api.infrastructure.tts

enum class TtsProviderType(
    val apiValue: String
) {
    ELEVENLABS("elevenlabs"),
    INWORLD("inworld");

    companion object {
        fun fromApiValue(value: String): TtsProviderType {
            return entries.firstOrNull { it.apiValue == value.lowercase() }
                ?: throw IllegalArgumentException("Unsupported TTS provider '$value'")
        }
    }
}
