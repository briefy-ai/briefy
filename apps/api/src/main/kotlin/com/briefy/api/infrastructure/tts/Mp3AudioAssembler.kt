package com.briefy.api.infrastructure.tts

import java.io.ByteArrayOutputStream

object Mp3AudioAssembler {
    fun concatenate(chunks: List<ByteArray>): ByteArray {
        if (chunks.isEmpty()) return ByteArray(0)
        if (chunks.size == 1) return chunks.first()

        val assembled = ByteArrayOutputStream(chunks.sumOf { it.size })
        chunks.forEachIndexed { index, chunk ->
            val bytes = if (index == 0) chunk else stripLeadingId3(chunk)
            assembled.write(bytes)
        }

        return assembled.toByteArray()
    }

    private fun stripLeadingId3(audioBytes: ByteArray): ByteArray {
        val headerSize = id3v2Size(audioBytes)
        if (headerSize <= 0 || headerSize >= audioBytes.size) {
            return audioBytes
        }
        return audioBytes.copyOfRange(headerSize, audioBytes.size)
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
}
