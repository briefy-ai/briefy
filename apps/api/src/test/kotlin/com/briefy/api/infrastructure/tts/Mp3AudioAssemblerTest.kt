package com.briefy.api.infrastructure.tts

import kotlin.test.Test
import kotlin.test.assertContentEquals

class Mp3AudioAssemblerTest {
    @Test
    fun `concatenate strips xing metadata frames from every chunk`() {
        val firstAudioFrame = audioFrame(0x11.toByte())
        val secondAudioFrame = audioFrame(0x22.toByte())
        val firstChunk = id3Header() + xingFrame() + firstAudioFrame
        val secondChunk = id3Header() + xingFrame() + secondAudioFrame

        val assembled = Mp3AudioAssembler.concatenate(listOf(firstChunk, secondChunk))

        assertContentEquals(id3Header() + firstAudioFrame + secondAudioFrame, assembled)
    }

    @Test
    fun `concatenate preserves audio frames when chunks have only id3 headers`() {
        val firstAudioFrame = audioFrame(0x33.toByte())
        val secondAudioFrame = audioFrame(0x44.toByte())
        val firstChunk = id3Header() + firstAudioFrame
        val secondChunk = id3Header() + secondAudioFrame

        val assembled = Mp3AudioAssembler.concatenate(listOf(firstChunk, secondChunk))

        assertContentEquals(firstChunk + secondAudioFrame, assembled)
    }

    private fun xingFrame(): ByteArray = frameWithMarker("Xing", 0x01)

    private fun audioFrame(fill: Byte): ByteArray = frameWithMarker(null, fill.toInt())

    private fun frameWithMarker(marker: String?, fill: Int): ByteArray {
        val frame = ByteArray(FRAME_LENGTH) { fill.toByte() }
        HEADER_BYTES.copyInto(frame, destinationOffset = 0)
        marker?.encodeToByteArray()?.copyInto(frame, destinationOffset = XING_OFFSET)
        return frame
    }

    private fun id3Header(): ByteArray = byteArrayOf(
        'I'.code.toByte(),
        'D'.code.toByte(),
        '3'.code.toByte(),
        4,
        0,
        0,
        0,
        0,
        0,
        0
    )

    private companion object {
        private val HEADER_BYTES = byteArrayOf(
            0xFF.toByte(),
            0xFB.toByte(),
            0x90.toByte(),
            0x64.toByte()
        )
        private const val FRAME_LENGTH = 417
        private const val XING_OFFSET = 36
    }
}
