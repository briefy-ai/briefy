package com.briefy.api.infrastructure.tts

import java.io.ByteArrayOutputStream

object Mp3AudioAssembler {
    fun concatenate(chunks: List<ByteArray>): ByteArray {
        if (chunks.isEmpty()) return ByteArray(0)
        if (chunks.size == 1) return chunks.first()

        val sanitizedChunks = chunks
            .mapIndexed(::sanitizeChunk)
            .filter { it.isNotEmpty() }
        if (sanitizedChunks.isEmpty()) return ByteArray(0)

        val assembled = ByteArrayOutputStream(sanitizedChunks.sumOf { it.size })
        sanitizedChunks.forEach(assembled::write)
        return assembled.toByteArray()
    }

    private fun sanitizeChunk(index: Int, audioBytes: ByteArray): ByteArray {
        return if (index == 0) {
            sanitizeFirstChunk(audioBytes)
        } else {
            stripLeadingVbrHeader(stripLeadingId3(audioBytes))
        }
    }

    private fun sanitizeFirstChunk(audioBytes: ByteArray): ByteArray {
        val headerSize = id3v2Size(audioBytes)
        if (headerSize <= 0 || headerSize >= audioBytes.size) {
            return stripLeadingVbrHeader(audioBytes)
        }

        val prefix = audioBytes.copyOfRange(0, headerSize)
        val body = audioBytes.copyOfRange(headerSize, audioBytes.size)
        val sanitizedBody = stripLeadingVbrHeader(body)
        return prefix + sanitizedBody
    }

    private fun stripLeadingId3(audioBytes: ByteArray): ByteArray {
        val headerSize = id3v2Size(audioBytes)
        if (headerSize <= 0 || headerSize >= audioBytes.size) {
            return audioBytes
        }
        return audioBytes.copyOfRange(headerSize, audioBytes.size)
    }

    private fun stripLeadingVbrHeader(audioBytes: ByteArray): ByteArray {
        val frame = parseFirstFrame(audioBytes) ?: return audioBytes
        if (!frame.hasXingMarker(audioBytes)) {
            return audioBytes
        }
        if (frame.length >= audioBytes.size) {
            return ByteArray(0)
        }
        return audioBytes.copyOfRange(frame.length, audioBytes.size)
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
        val hasFooter = (audioBytes[5].toInt() and 0x10) != 0
        return 10 + size + if (hasFooter) 10 else 0
    }

    private fun parseFirstFrame(audioBytes: ByteArray): Mp3Frame? {
        if (audioBytes.size < 4) return null

        val header = ((audioBytes[0].toInt() and 0xFF) shl 24) or
            ((audioBytes[1].toInt() and 0xFF) shl 16) or
            ((audioBytes[2].toInt() and 0xFF) shl 8) or
            (audioBytes[3].toInt() and 0xFF)
        if ((header and FRAME_SYNC_MASK) != FRAME_SYNC_MASK) {
            return null
        }

        val versionBits = (header shr 19) and 0b11
        val layerBits = (header shr 17) and 0b11
        val bitrateIndex = (header shr 12) and 0b1111
        val sampleRateIndex = (header shr 10) and 0b11
        val paddingBit = (header shr 9) and 0b1
        val hasCrc = ((header shr 16) and 0b1) == 0
        val channelModeBits = (header shr 6) and 0b11

        if (layerBits != LAYER_III || bitrateIndex !in BITRATE_INDEX_RANGE || sampleRateIndex == RESERVED_SAMPLE_RATE_INDEX) {
            return null
        }

        val version = mpegVersion(versionBits) ?: return null
        val bitrateKbps = bitrateKbps(version, bitrateIndex) ?: return null
        val sampleRateHz = sampleRateHz(version, sampleRateIndex)
        val frameLength = frameLength(version, bitrateKbps, sampleRateHz, paddingBit)
        if (frameLength <= 0) {
            return null
        }

        return Mp3Frame(
            length = frameLength,
            hasCrc = hasCrc,
            version = version,
            channelModeBits = channelModeBits
        )
    }

    private fun bitrateKbps(version: MpegVersion, bitrateIndex: Int): Int? {
        val table = when (version) {
            MpegVersion.V1 -> MPEG1_LAYER3_BITRATES
            MpegVersion.V2, MpegVersion.V25 -> MPEG2_LAYER3_BITRATES
        }
        val bitrate = table[bitrateIndex]
        return bitrate.takeIf { it > 0 }
    }

    private fun sampleRateHz(version: MpegVersion, sampleRateIndex: Int): Int {
        return when (version) {
            MpegVersion.V1 -> MPEG1_SAMPLE_RATES[sampleRateIndex]
            MpegVersion.V2 -> MPEG2_SAMPLE_RATES[sampleRateIndex]
            MpegVersion.V25 -> MPEG25_SAMPLE_RATES[sampleRateIndex]
        }
    }

    private fun frameLength(version: MpegVersion, bitrateKbps: Int, sampleRateHz: Int, paddingBit: Int): Int {
        val coefficient = when (version) {
            MpegVersion.V1 -> 144
            MpegVersion.V2, MpegVersion.V25 -> 72
        }
        return ((coefficient * bitrateKbps * 1000) / sampleRateHz) + paddingBit
    }

    private data class Mp3Frame(
        val length: Int,
        val hasCrc: Boolean,
        val version: MpegVersion,
        val channelModeBits: Int
    ) {
        fun hasXingMarker(audioBytes: ByteArray): Boolean {
            val sideInfoSize = when (version) {
                MpegVersion.V1 -> if (channelModeBits == CHANNEL_MODE_MONO) 17 else 32
                MpegVersion.V2, MpegVersion.V25 -> if (channelModeBits == CHANNEL_MODE_MONO) 9 else 17
            }
            val markerOffset = 4 + (if (hasCrc) 2 else 0) + sideInfoSize
            if (markerOffset + 4 > audioBytes.size) {
                return false
            }

            val marker = audioBytes.copyOfRange(markerOffset, markerOffset + 4).decodeToString()
            return marker == "Xing" || marker == "Info"
        }
    }

    private enum class MpegVersion {
        V1,
        V2,
        V25
    }

    private fun mpegVersion(versionBits: Int): MpegVersion? = when (versionBits) {
        0b11 -> MpegVersion.V1
        0b10 -> MpegVersion.V2
        0b00 -> MpegVersion.V25
        else -> null
    }

    private const val FRAME_SYNC_MASK = -0x200000
    private const val LAYER_III = 0b01
    private const val CHANNEL_MODE_MONO = 0b11
    private const val RESERVED_SAMPLE_RATE_INDEX = 0b11
    private val BITRATE_INDEX_RANGE = 1..14
    private val MPEG1_LAYER3_BITRATES = intArrayOf(
        0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0
    )
    private val MPEG2_LAYER3_BITRATES = intArrayOf(
        0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0
    )
    private val MPEG1_SAMPLE_RATES = intArrayOf(44_100, 48_000, 32_000, 0)
    private val MPEG2_SAMPLE_RATES = intArrayOf(22_050, 24_000, 16_000, 0)
    private val MPEG25_SAMPLE_RATES = intArrayOf(11_025, 12_000, 8_000, 0)
}
