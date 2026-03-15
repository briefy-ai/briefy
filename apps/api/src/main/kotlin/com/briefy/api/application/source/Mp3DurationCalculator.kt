package com.briefy.api.application.source

import kotlin.math.roundToInt

object Mp3DurationCalculator {
    fun calculate(audioBytes: ByteArray): Int {
        if (audioBytes.isEmpty()) return 0

        val tagOffset = id3v2Size(audioBytes)
        val bitrateKbps = firstFrameBitrateKbps(audioBytes, tagOffset)
        if (bitrateKbps == null || bitrateKbps <= 0) {
            return 0
        }

        val audioPayloadBytes = (audioBytes.size - tagOffset).coerceAtLeast(0)
        return ((audioPayloadBytes * 8.0) / (bitrateKbps * 1000.0)).roundToInt()
    }

    private fun id3v2Size(audioBytes: ByteArray): Int {
        if (audioBytes.size < 10) return 0
        if (audioBytes[0] != 'I'.code.toByte() || audioBytes[1] != 'D'.code.toByte() || audioBytes[2] != '3'.code.toByte()) {
            return 0
        }

        val size = ((audioBytes[6].toInt() and 0x7F) shl 21) or
            ((audioBytes[7].toInt() and 0x7F) shl 14) or
            ((audioBytes[8].toInt() and 0x7F) shl 7) or
            (audioBytes[9].toInt() and 0x7F)
        return 10 + size
    }

    private fun firstFrameBitrateKbps(audioBytes: ByteArray, startOffset: Int): Int? {
        var index = startOffset.coerceAtLeast(0)
        while (index + 3 < audioBytes.size) {
            val header = ((audioBytes[index].toInt() and 0xFF) shl 24) or
                ((audioBytes[index + 1].toInt() and 0xFF) shl 16) or
                ((audioBytes[index + 2].toInt() and 0xFF) shl 8) or
                (audioBytes[index + 3].toInt() and 0xFF)

            if ((header and FRAME_SYNC_MASK) == FRAME_SYNC_MASK) {
                val versionBits = (header shr 19) and 0b11
                val layerBits = (header shr 17) and 0b11
                val bitrateIndex = (header shr 12) and 0b1111

                if (versionBits == MPEG_VERSION_1 && layerBits == LAYER_III && bitrateIndex in BITRATE_INDEX_RANGE) {
                    return MPEG1_LAYER3_BITRATES[bitrateIndex]
                }
            }

            index++
        }
        return null
    }

    private const val FRAME_SYNC_MASK = -0x200000
    private const val MPEG_VERSION_1 = 0b11
    private const val LAYER_III = 0b01
    private val BITRATE_INDEX_RANGE = 1..14
    private val MPEG1_LAYER3_BITRATES = intArrayOf(
        0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0
    )
}
