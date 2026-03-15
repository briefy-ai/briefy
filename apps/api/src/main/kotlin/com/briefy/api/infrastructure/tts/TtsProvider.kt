package com.briefy.api.infrastructure.tts

data class TtsSynthesisRequest(
    val text: String,
    val apiKey: String,
    val voiceId: String,
    val modelId: String
)

interface TtsProvider {
    val type: TtsProviderType

    fun synthesize(request: TtsSynthesisRequest): ByteArray
}
