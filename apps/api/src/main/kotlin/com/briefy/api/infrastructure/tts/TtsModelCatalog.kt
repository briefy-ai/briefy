package com.briefy.api.infrastructure.tts

import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

data class TtsModelPricing(
    val id: String,
    val label: String,
    val pricePerMillionCharsUsd: Double
) {
    fun estimateCostUsd(characterCount: Int): BigDecimal {
        if (characterCount <= 0) return BigDecimal.ZERO.setScale(2)
        return BigDecimal.valueOf(characterCount.toDouble())
            .divide(BigDecimal.valueOf(1_000_000.0), 8, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(pricePerMillionCharsUsd))
            .setScale(2, RoundingMode.CEILING)
    }

    fun estimatedCostPerMinuteUsd(charsPerMinute: Int): BigDecimal = estimateCostUsd(charsPerMinute)

    fun estimatedCostTenMinutesUsd(charsPerMinute: Int): BigDecimal = estimateCostUsd(charsPerMinute * 10)
}

@Component
class TtsModelCatalog {
    fun modelsFor(type: TtsProviderType): List<TtsModelPricing> = models.getValue(type)

    fun modelFor(type: TtsProviderType, modelId: String): TtsModelPricing {
        return modelsFor(type).firstOrNull { it.id == modelId }
            ?: throw IllegalArgumentException("Unsupported model '$modelId' for provider '${type.apiValue}'")
    }

    val defaultElevenLabsModelId: String
        get() = "eleven_flash_v2_5"

    val defaultInworldModelId: String
        get() = "inworld-tts-1.5-mini"

    private val models = mapOf(
        TtsProviderType.ELEVENLABS to listOf(
            TtsModelPricing(
                id = "eleven_flash_v2_5",
                label = "Flash v2.5",
                pricePerMillionCharsUsd = 60.0
            ),
            TtsModelPricing(
                id = "eleven_turbo_v2_5",
                label = "Turbo v2.5",
                pricePerMillionCharsUsd = 60.0
            ),
            TtsModelPricing(
                id = "eleven_multilingual_v2",
                label = "Multilingual v2",
                pricePerMillionCharsUsd = 120.0
            )
        ),
        TtsProviderType.INWORLD to listOf(
            TtsModelPricing(
                id = "inworld-tts-1.5-mini",
                label = "Inworld TTS 1.5 Mini",
                pricePerMillionCharsUsd = 5.0
            ),
            TtsModelPricing(
                id = "inworld-tts-1.5-max",
                label = "Inworld TTS 1.5 Max",
                pricePerMillionCharsUsd = 10.0
            )
        )
    )
}
