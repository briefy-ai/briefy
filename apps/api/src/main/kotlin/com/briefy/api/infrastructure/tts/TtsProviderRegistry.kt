package com.briefy.api.infrastructure.tts

import org.springframework.stereotype.Component

@Component
class TtsProviderRegistry(
    providers: List<TtsProvider>
) {
    private val providersByType = providers.associateBy { it.type }

    fun get(type: TtsProviderType): TtsProvider {
        return providersByType[type]
            ?: throw IllegalStateException("No TTS provider registered for ${type.apiValue}")
    }
}
